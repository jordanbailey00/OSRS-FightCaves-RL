package content.area.karamja.tzhaar_city

import content.entity.player.effect.energy.runEnergy
import content.quest.instance
import content.skill.prayer.PrayerConfigs
import world.gregs.voidps.engine.GameLoop
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.Players
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.move.running
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.type.Tile
import java.io.File
import java.util.Properties

private const val BACKEND_CONTROL_ENABLED = "fightCave.demo.backendControl.enabled"
private const val BACKEND_CONTROL_ROOT = "fightCave.demo.backendControl.root"
private const val BACKEND_CONTROL_ROOT_DEFAULT = "./data/fight_caves_demo/backend_control/"

class FightCaveDemoBackendControl(
    private val adapter: FightCaveBackendActionAdapter,
) : Runnable {
    private val observationBuilder = FightCaveHeadedObservationBuilder(adapter)

    override fun run() {
        if (!Settings[BACKEND_CONTROL_ENABLED, false]) {
            return
        }
        val requestFile = requestFiles().firstOrNull() ?: return
        process(requestFile)
    }

    private fun process(file: File) {
        val request = try {
            readRequest(file)
        } catch (ex: Exception) {
            writeErrorResult(
                requestId = file.nameWithoutExtension,
                account = "",
                payload = orderedMapOf(
                    "status" to "invalid_request",
                    "error" to (ex.message ?: ex::class.simpleName.orEmpty()),
                    "request_path" to file.absolutePath,
                    "processed_tick" to GameLoop.tick,
                ),
            )
            file.delete()
            return
        }

        val player = resolvePlayer(request.account) ?: return
        if (!player["fight_cave_demo_episode", false] || player.instance() == null) {
            return
        }

        val visibleBefore = adapter.visibleNpcTargets(player)
        val beforeState = playerState(player)
        val observationBefore = observationBuilder.build(player)
        val result = adapter.apply(player, request.action)
        val visibleAfter = adapter.visibleNpcTargets(player)
        val afterState = playerState(player)
        val observationAfter = observationBuilder.build(player)

        val payload = orderedMapOf(
            "schema_id" to "headless_action_v1",
            "schema_version" to 1,
            "request_id" to request.requestId,
            "account" to request.account,
            "request_context" to request.context,
            "request_path" to file.absolutePath,
            "request_submitted_at_ms" to request.submittedAtMs,
            "processed_at_ms" to System.currentTimeMillis(),
            "processed_tick" to GameLoop.tick,
            "action" to request.action.toWireMap(),
            "visible_targets_before" to visibleTargetsToMaps(visibleBefore),
            "visible_targets_after" to visibleTargetsToMaps(visibleAfter),
            "player_state_before" to beforeState,
            "player_state_after" to afterState,
            "observation_before" to observationBefore,
            "observation_after" to observationAfter,
            "action_result" to orderedMapOf(
                "action_id" to result.actionId,
                "action_name" to result.actionType.wireName,
                "action_applied" to result.actionApplied,
                "rejection_reason" to result.rejectionReason?.name,
                "metadata" to linkedMapOf<String, Any?>().apply {
                    for ((key, value) in result.metadata) {
                        put(key, value)
                    }
                },
            ),
        )
        writeResult(request.requestId, payload)
        FightCaveDemoObservability.recordBackendAction(
            player = player,
            requestId = request.requestId,
            requestContext = request.context,
            action = request.action,
            result = result,
            visibleTargetsBefore = visibleBefore,
            visibleTargetsAfter = visibleAfter,
        )
        file.delete()
    }

    private fun requestFiles(): List<File> =
        requestDirectory()
            .listFiles { child -> child.isFile && child.extension == "properties" }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun writeErrorResult(requestId: String, account: String, payload: LinkedHashMap<String, Any?>) {
        val resultFile = resultDirectory().resolve("$requestId.json")
        resultFile.parentFile.mkdirs()
        resultFile.writeText(payload.toJson())
    }

    private fun writeResult(requestId: String, payload: LinkedHashMap<String, Any?>) {
        val resultFile = resultDirectory().resolve("$requestId.json")
        resultFile.parentFile.mkdirs()
        resultFile.writeText(payload.toJson())
    }

    private fun readRequest(file: File): BackendActionRequest {
        val properties = Properties()
        file.inputStream().use(properties::load)
        val requestId = properties.getProperty("request_id")?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
        val account = properties.getProperty("account")?.takeIf { it.isNotBlank() }
            ?: error("backend action request missing account")
        val action = parseAction(properties)
        return BackendActionRequest(
            requestId = requestId,
            account = account,
            submittedAtMs = properties.getProperty("submitted_at_ms")?.toLongOrNull(),
            context = parseRequestContext(properties),
            action = action,
        )
    }

    private fun parseRequestContext(properties: Properties): LinkedHashMap<String, Any?> {
        val context = linkedMapOf<String, Any?>()
        for ((rawKey, rawValue) in properties.entries) {
            val key = rawKey.toString()
            if (key in REQUEST_CORE_KEYS) {
                continue
            }
            context[key] = rawValue?.toString()
        }
        return context
    }

    private fun parseAction(properties: Properties): FightCaveBackendAction {
        val actionType =
            properties.getProperty("action_id")?.toIntOrNull()?.let(FightCaveBackendActionType::fromId)
                ?: properties.getProperty("name")?.let(FightCaveBackendActionType::fromWireName)
                ?: error("backend action request missing action_id or name")
        return when (actionType) {
            FightCaveBackendActionType.Wait -> FightCaveBackendAction.Wait
            FightCaveBackendActionType.WalkToTile -> {
                val x = properties.getProperty("x")?.toIntOrNull() ?: error("walk_to_tile missing x")
                val y = properties.getProperty("y")?.toIntOrNull() ?: error("walk_to_tile missing y")
                val level = properties.getProperty("level")?.toIntOrNull() ?: 0
                FightCaveBackendAction.WalkToTile(Tile(x, y, level))
            }
            FightCaveBackendActionType.AttackVisibleNpc -> {
                val visibleNpcIndex =
                    properties.getProperty("visible_npc_index")?.toIntOrNull()
                        ?: error("attack_visible_npc missing visible_npc_index")
                FightCaveBackendAction.AttackVisibleNpc(visibleNpcIndex)
            }
            FightCaveBackendActionType.ToggleProtectionPrayer -> {
                val prayerId = properties.getProperty("prayer") ?: error("toggle_protection_prayer missing prayer")
                val prayer =
                    FightCaveBackendProtectionPrayer.fromPrayerId(prayerId)
                        ?: error("unsupported protection prayer: $prayerId")
                FightCaveBackendAction.ToggleProtectionPrayer(prayer)
            }
            FightCaveBackendActionType.EatShark -> FightCaveBackendAction.EatShark
            FightCaveBackendActionType.DrinkPrayerPotion -> FightCaveBackendAction.DrinkPrayerPotion
            FightCaveBackendActionType.ToggleRun -> FightCaveBackendAction.ToggleRun
        }
    }

    private fun resolvePlayer(account: String): Player? = Players.firstOrNull { it.accountName.equals(account, ignoreCase = true) }

    private fun playerState(player: Player): LinkedHashMap<String, Any?> = orderedMapOf(
        "tile" to tileMap(player.tile),
        "wave" to player["fight_cave_wave", 0],
        "instance_id" to player.instance()?.id,
        "run_energy" to player.runEnergy,
        "running" to player.running,
        "prayer_level" to player.levels.get(Skill.Prayer),
        "prayer_book" to player[PrayerConfigs.PRAYERS, PrayerConfigs.BOOK_PRAYERS],
        "active_prayers" to player[PrayerConfigs.ACTIVE_PRAYERS, emptyList<String>()],
        "active_curses" to player[PrayerConfigs.ACTIVE_CURSES, emptyList<String>()],
        "inventory_counts" to orderedMapOf(
            "sharks" to player.inventory.count("shark"),
            "prayer_potions_total" to PRAYER_POTION_IDS.sumOf(player.inventory::count),
        ),
    )

    private fun visibleTargetsToMaps(targets: List<FightCaveBackendVisibleNpcTarget>): List<LinkedHashMap<String, Any?>> =
        targets.map { target ->
            orderedMapOf(
                "visible_index" to target.visibleIndex,
                "npc_index" to target.npcIndex,
                "id" to target.id,
                "tile" to tileMap(target.tile),
            )
        }

    private fun requestDirectory(): File = backendControlRoot().resolve("inbox")
    private fun resultDirectory(): File = backendControlRoot().resolve("results")
    private fun backendControlRoot(): File = File(Settings[BACKEND_CONTROL_ROOT, BACKEND_CONTROL_ROOT_DEFAULT])

    private fun orderedMapOf(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

    private fun tileMap(tile: Tile): LinkedHashMap<String, Any?> = orderedMapOf(
        "x" to tile.x,
        "y" to tile.y,
        "level" to tile.level,
    )

    private fun FightCaveBackendAction.toWireMap(): LinkedHashMap<String, Any?> =
        when (this) {
            FightCaveBackendAction.Wait -> orderedMapOf("action_id" to type.id, "name" to type.wireName)
            is FightCaveBackendAction.WalkToTile ->
                orderedMapOf(
                    "action_id" to type.id,
                    "name" to type.wireName,
                    "tile" to tileMap(tile),
                )
            is FightCaveBackendAction.AttackVisibleNpc ->
                orderedMapOf(
                    "action_id" to type.id,
                    "name" to type.wireName,
                    "visible_npc_index" to visibleNpcIndex,
                )
            is FightCaveBackendAction.ToggleProtectionPrayer ->
                orderedMapOf(
                    "action_id" to type.id,
                    "name" to type.wireName,
                    "prayer" to prayer.prayerId,
                )
            FightCaveBackendAction.EatShark -> orderedMapOf("action_id" to type.id, "name" to type.wireName)
            FightCaveBackendAction.DrinkPrayerPotion -> orderedMapOf("action_id" to type.id, "name" to type.wireName)
            FightCaveBackendAction.ToggleRun -> orderedMapOf("action_id" to type.id, "name" to type.wireName)
        }

    private fun LinkedHashMap<String, Any?>.toJson(): String = entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "\"${escape(key)}\":${value.toJsonValue()}"
    }

    private fun Any?.toJsonValue(): String =
        when (this) {
            null -> "null"
            is String -> "\"${escape(this)}\""
            is Number, is Boolean -> toString()
            is LinkedHashMap<*, *> -> {
                val converted = linkedMapOf<String, Any?>()
                for ((key, value) in this) {
                    converted[key.toString()] = value
                }
                converted.toJson()
            }
            is Map<*, *> -> {
                val converted = linkedMapOf<String, Any?>()
                for ((key, value) in this) {
                    converted[key.toString()] = value
                }
                converted.toJson()
            }
            is Iterable<*> -> joinToString(prefix = "[", postfix = "]") { it.toJsonValue() }
            else -> "\"${escape(toString())}\""
        }

    private fun escape(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

    private data class BackendActionRequest(
        val requestId: String,
        val account: String,
        val submittedAtMs: Long?,
        val context: LinkedHashMap<String, Any?>,
        val action: FightCaveBackendAction,
    )

    companion object {
        private val PRAYER_POTION_IDS = setOf("prayer_potion_4", "prayer_potion_3", "prayer_potion_2", "prayer_potion_1")
        private val REQUEST_CORE_KEYS = setOf(
            "request_id",
            "account",
            "submitted_at_ms",
            "action_id",
            "name",
            "x",
            "y",
            "level",
            "visible_npc_index",
            "prayer",
        )
    }
}
