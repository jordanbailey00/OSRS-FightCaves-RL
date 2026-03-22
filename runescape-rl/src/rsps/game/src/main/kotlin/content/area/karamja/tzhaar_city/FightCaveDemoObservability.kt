package content.area.karamja.tzhaar_city

import content.entity.player.effect.energy.runEnergy
import content.quest.instance
import content.skill.prayer.PrayerConfigs
import world.gregs.voidps.engine.GameLoop
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.character.npc.NPCs
import world.gregs.voidps.engine.entity.character.move.running
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.inv.equipment
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.engine.queue.softQueue
import world.gregs.voidps.network.login.protocol.visual.update.player.EquipSlot
import world.gregs.voidps.type.Tile
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val FIGHT_CAVE_DEMO_PROFILE_ID = "fight-caves-demo"
private const val FIGHT_CAVE_DEMO_ARTIFACT_ROOT_SETTING = "fightCave.demo.artifacts.path"
private const val FIGHT_CAVE_DEMO_ARTIFACT_ROOT_DEFAULT = "./data/fight_caves_demo/artifacts/"
private const val FIGHT_CAVE_DEMO_SESSION_ID_KEY = "fight_cave_demo_session_id"
private const val FIGHT_CAVE_DEMO_SESSION_STARTED_AT_KEY = "fight_cave_demo_session_started_at"
private const val FIGHT_CAVE_DEMO_SESSION_LOG_PATH_KEY = "fight_cave_demo_session_log_path"
private const val FIGHT_CAVE_DEMO_CHECKLIST_PATH_KEY = "fight_cave_demo_checklist_path"
private const val FIGHT_CAVE_DEMO_LAST_MANIFEST_PATH_KEY = "fight_cave_demo_last_manifest_path"
private const val FIGHT_CAVE_DEMO_RESET_COUNT_KEY = "fight_cave_demo_reset_count"
private const val FIGHT_CAVE_DEMO_ENTRY_COUNT_KEY = "fight_cave_demo_entry_count"
private const val FIGHT_CAVE_DEMO_LEAVE_COUNT_KEY = "fight_cave_demo_leave_count"
private const val FIGHT_CAVE_DEMO_WORLD_ACCESS_COUNT_KEY = "fight_cave_demo_world_access_count"
private const val FIGHT_CAVE_DEMO_RESET_FOLLOWUP_DELAY_TICKS = 8

object FightCaveDemoObservability {
    private val idFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss.SSS")

    fun beginSession(player: Player) {
        val sessionId = "${player.accountName}-${idFormat.format(LocalDateTime.now())}"
        player[FIGHT_CAVE_DEMO_SESSION_ID_KEY] = sessionId
        player[FIGHT_CAVE_DEMO_SESSION_STARTED_AT_KEY] = System.currentTimeMillis()
        player[FIGHT_CAVE_DEMO_RESET_COUNT_KEY] = 0
        player[FIGHT_CAVE_DEMO_ENTRY_COUNT_KEY] = 0
        player[FIGHT_CAVE_DEMO_LEAVE_COUNT_KEY] = 0
        player[FIGHT_CAVE_DEMO_WORLD_ACCESS_COUNT_KEY] = 0

        val sessionLog = sessionLogFile(sessionId)
        val checklist = checklistFile(sessionId)
        player[FIGHT_CAVE_DEMO_SESSION_LOG_PATH_KEY] = sessionLog.absolutePath
        player[FIGHT_CAVE_DEMO_CHECKLIST_PATH_KEY] = checklist.absolutePath
        player.clear(FIGHT_CAVE_DEMO_LAST_MANIFEST_PATH_KEY)

        appendEvent(
            player,
            "session_started",
            orderedMapOf(
                "profile" to FIGHT_CAVE_DEMO_PROFILE_ID,
                "tile" to tileMap(player.tile),
                "artifact_paths" to artifactPaths(player),
            ),
        )
        writeChecklist(player)
    }

    fun recordEntry(player: Player, cause: String) {
        ensureSession(player)
        player[FIGHT_CAVE_DEMO_ENTRY_COUNT_KEY] = player[FIGHT_CAVE_DEMO_ENTRY_COUNT_KEY, 0] + 1
        appendEvent(
            player,
            "entry_requested",
            orderedMapOf(
                "cause" to cause,
                "tile" to tileMap(player.tile),
                "entry_count" to player[FIGHT_CAVE_DEMO_ENTRY_COUNT_KEY, 0],
            ),
        )
        writeChecklist(player)
    }

    fun recordLeave(player: Player, cause: String, wave: Int) {
        ensureSession(player)
        player[FIGHT_CAVE_DEMO_LEAVE_COUNT_KEY] = player[FIGHT_CAVE_DEMO_LEAVE_COUNT_KEY, 0] + 1
        appendEvent(
            player,
            "leave_requested",
            orderedMapOf(
                "cause" to cause,
                "wave" to wave,
                "tile" to tileMap(player.tile),
                "leave_count" to player[FIGHT_CAVE_DEMO_LEAVE_COUNT_KEY, 0],
            ),
        )
        writeChecklist(player)
    }

    fun recordWorldAccessViolation(player: Player, cause: String, tile: Tile) {
        ensureSession(player)
        player[FIGHT_CAVE_DEMO_WORLD_ACCESS_COUNT_KEY] = player[FIGHT_CAVE_DEMO_WORLD_ACCESS_COUNT_KEY, 0] + 1
        appendEvent(
            player,
            "world_access_violation",
            orderedMapOf(
                "cause" to cause,
                "tile" to tileMap(tile),
                "violation_count" to player[FIGHT_CAVE_DEMO_WORLD_ACCESS_COUNT_KEY, 0],
            ),
        )
        writeChecklist(player)
    }

    fun recordDeathResetReady(player: Player, waitedTicks: Int, dead: Boolean, deathQueueActive: Boolean, disconnected: Boolean) {
        ensureSession(player)
        appendEvent(
            player,
            "death_reset_ready",
            orderedMapOf(
                "waited_ticks" to waitedTicks,
                "dead" to dead,
                "death_queue_active" to deathQueueActive,
                "disconnected" to disconnected,
                "tile" to tileMap(player.tile),
                "instance_id" to player.instance()?.id,
            ),
        )
    }

    fun recordBackendAction(
        player: Player,
        requestId: String,
        requestContext: LinkedHashMap<String, Any?>,
        action: FightCaveBackendAction,
        result: FightCaveBackendActionResult,
        visibleTargetsBefore: List<FightCaveBackendVisibleNpcTarget>,
        visibleTargetsAfter: List<FightCaveBackendVisibleNpcTarget>,
    ) {
        ensureSession(player)
        appendEvent(
            player,
            "backend_action_processed",
            orderedMapOf(
                "request_id" to requestId,
                "request_context" to requestContext,
                "action_id" to result.actionId,
                "action_name" to result.actionType.wireName,
                "action_applied" to result.actionApplied,
                "rejection_reason" to result.rejectionReason?.name,
                "action" to action.toWireMap(),
                "visible_targets_before" to visibleTargetsBefore.map { target ->
                    orderedMapOf(
                        "visible_index" to target.visibleIndex,
                        "npc_index" to target.npcIndex,
                        "id" to target.id,
                        "tile" to tileMap(target.tile),
                    )
                },
                "visible_targets_after" to visibleTargetsAfter.map { target ->
                    orderedMapOf(
                        "visible_index" to target.visibleIndex,
                        "npc_index" to target.npcIndex,
                        "id" to target.id,
                        "tile" to tileMap(target.tile),
                    )
                },
                "result_metadata" to result.metadata,
            ),
        )
    }

    fun recordReset(player: Player, cause: String, config: FightCaveEpisodeConfig, state: FightCaveEpisodeState) {
        ensureSession(player)
        val resetIndex = player[FIGHT_CAVE_DEMO_RESET_COUNT_KEY, 0] + 1
        player[FIGHT_CAVE_DEMO_RESET_COUNT_KEY] = resetIndex
        val spawnedNpcs = spawnedNpcSnapshot(player)

        val manifest = manifestFile(sessionId(player), resetIndex)
        manifest.parentFile.mkdirs()
        manifest.writeText(buildStarterStateManifest(player, cause, config, state, resetIndex, spawnedNpcs))
        player[FIGHT_CAVE_DEMO_LAST_MANIFEST_PATH_KEY] = manifest.absolutePath

        appendEvent(
            player,
            "starter_state_manifest_written",
            orderedMapOf(
                "cause" to cause,
                "reset_index" to resetIndex,
                "manifest_path" to manifest.absolutePath,
            ),
        )
        appendEvent(
            player,
            "episode_reset",
            orderedMapOf(
                "cause" to cause,
                "reset_index" to resetIndex,
                "seed" to state.seed,
                "wave" to state.wave,
                "rotation" to state.rotation,
                "remaining" to state.remaining,
                "instance_id" to state.instanceId,
                "player_tile" to tileMap(state.playerTile),
                "spawned_npc_count" to spawnedNpcs.size,
                "spawned_npcs" to spawnedNpcs,
            ),
        )
        player.softQueue("fight_cave_demo_reset_followup", FIGHT_CAVE_DEMO_RESET_FOLLOWUP_DELAY_TICKS) {
            val followupNpcs = spawnedNpcSnapshot(player)
            appendEvent(
                player,
                "episode_reset_followup",
                orderedMapOf(
                    "cause" to cause,
                    "reset_index" to resetIndex,
                    "instance_id" to player.instance()?.id,
                    "player_tile" to tileMap(player.tile),
                    "dead" to player["dead", false],
                    "spawned_npc_count" to followupNpcs.size,
                    "spawned_npcs" to followupNpcs,
                ),
            )
            writeChecklist(player)
        }
        writeChecklist(player)
    }

    private fun ensureSession(player: Player) {
        if (!player.contains(FIGHT_CAVE_DEMO_SESSION_ID_KEY)) {
            beginSession(player)
        }
    }

    private fun appendEvent(player: Player, event: String, data: LinkedHashMap<String, Any?>) {
        val payload = orderedMapOf(
            "timestamp_ms" to System.currentTimeMillis(),
            "tick" to GameLoop.tick,
            "profile" to FIGHT_CAVE_DEMO_PROFILE_ID,
            "session_id" to sessionId(player),
            "account" to player.accountName,
            "event" to event,
        )
        payload.putAll(data)
        val file = File(player[FIGHT_CAVE_DEMO_SESSION_LOG_PATH_KEY, sessionLogFile(sessionId(player)).absolutePath])
        file.parentFile.mkdirs()
        file.appendText(payload.toJson())
        file.appendText("\n")
    }

    private fun buildStarterStateManifest(
        player: Player,
        cause: String,
        config: FightCaveEpisodeConfig,
        state: FightCaveEpisodeState,
        resetIndex: Int,
        spawnedNpcs: List<LinkedHashMap<String, Any?>>,
    ): String {
        val equipmentState =
            player.equipment.items.mapIndexedNotNull { index, item ->
                if (item.isEmpty()) {
                    null
                } else {
                    orderedMapOf(
                        "slot" to EquipSlot.by(index).name.lowercase(),
                        "index" to index,
                        "item_id" to item.id,
                        "amount" to item.amount,
                    )
                }
            }
        val inventoryState =
            player.inventory.items.mapIndexedNotNull { index, item ->
                if (item.isEmpty()) {
                    null
                } else {
                    orderedMapOf(
                        "slot" to index,
                        "item_id" to item.id,
                        "amount" to item.amount,
                    )
                }
            }
        val skillState = linkedMapOf<String, Any?>()
        for (skill in Skill.all) {
            skillState[skill.name.lowercase()] =
                orderedMapOf(
                    "level" to player.levels.get(skill),
                    "max_level" to player.levels.getMax(skill),
                    "blocked" to player.experience.blocked(skill),
                )
        }
        return orderedMapOf(
            "manifest_type" to "fight_cave_demo_starter_state",
            "profile" to FIGHT_CAVE_DEMO_PROFILE_ID,
            "source_contract" to "RLspec.md",
            "session_id" to sessionId(player),
            "account" to player.accountName,
            "cause" to cause,
            "reset_index" to resetIndex,
            "seed" to state.seed,
            "start_wave" to config.startWave,
            "wave" to state.wave,
            "rotation" to state.rotation,
            "remaining" to state.remaining,
            "instance_id" to state.instanceId,
            "player_tile" to tileMap(state.playerTile),
            "run_energy" to player.runEnergy,
            "running" to player.running,
            "skip_level_up" to player["skip_level_up", false],
            "prayers" to orderedMapOf(
                "prayer_book" to player[PrayerConfigs.PRAYERS, PrayerConfigs.BOOK_PRAYERS],
                "active_prayers" to player[PrayerConfigs.ACTIVE_PRAYERS, emptyList<Any>()],
                "active_curses" to player[PrayerConfigs.ACTIVE_CURSES, emptyList<Any>()],
                "using_quick_prayers" to player[PrayerConfigs.USING_QUICK_PRAYERS, false],
                "selecting_quick_prayers" to player[PrayerConfigs.SELECTING_QUICK_PRAYERS, false],
                "drain_timer_active" to player.softTimers.contains("prayer_drain"),
            ),
            "content_noise_controls" to orderedMapOf(
                "task_disable_popups" to player["task_disable_popups", false],
                "task_dont_show_again" to player["task_dont_show_again", false],
                "creation_seen" to player.contains("creation"),
            ),
            "spawned_npcs" to spawnedNpcs,
            "equipment" to equipmentState,
            "inventory" to inventoryState,
            "skills" to skillState,
            "artifact_paths" to artifactPaths(player),
        ).toJson()
    }

    private fun writeChecklist(player: Player) {
        val checklist = buildString {
            appendLine("# Fight Caves Demo Validation Checklist")
            appendLine()
            appendLine("Session ID: ${sessionId(player)}")
            appendLine("Account: ${player.accountName}")
            appendLine("Profile: $FIGHT_CAVE_DEMO_PROFILE_ID")
            appendLine()
            appendLine("Artifact Locations")
            appendLine("- Session log: ${player[FIGHT_CAVE_DEMO_SESSION_LOG_PATH_KEY, ""]}")
            appendLine("- Latest starter-state manifest: ${player[FIGHT_CAVE_DEMO_LAST_MANIFEST_PATH_KEY, "pending"]}")
            appendLine("- Checklist: ${player[FIGHT_CAVE_DEMO_CHECKLIST_PATH_KEY, ""]}")
            appendLine("- Audit TSV directory: ${File(Settings["storage.players.logs"]).absolutePath}")
            appendLine()
            appendLine("Auto-Observed")
            appendLine("- [x] Session log created")
            appendLine("- [${if (player[FIGHT_CAVE_DEMO_ENTRY_COUNT_KEY, 0] > 0) "x" else " "}] Demo entry logged")
            appendLine("- [${if (player[FIGHT_CAVE_DEMO_RESET_COUNT_KEY, 0] > 0) "x" else " "}] Starter-state manifest written")
            appendLine("- [${if (player[FIGHT_CAVE_DEMO_LEAVE_COUNT_KEY, 0] > 0) "x" else " "}] Leave/reset request logged")
            appendLine("- [${if (player[FIGHT_CAVE_DEMO_WORLD_ACCESS_COUNT_KEY, 0] > 0) "x" else " "}] World-access violation logged")
            appendLine()
            appendLine("Manual Follow-Up")
            appendLine("- [ ] Verify headed starter-state parity against RLspec")
            appendLine("- [ ] Verify no broader world access beyond Fight Caves")
            appendLine("- [ ] Verify combat, prayer, and inventory loop in headed play")
            appendLine("- [ ] Verify wave progression, death reset, and completion recycle")
            appendLine()
            appendLine("Observed Counters")
            appendLine("- Entry events: ${player[FIGHT_CAVE_DEMO_ENTRY_COUNT_KEY, 0]}")
            appendLine("- Reset events: ${player[FIGHT_CAVE_DEMO_RESET_COUNT_KEY, 0]}")
            appendLine("- Leave events: ${player[FIGHT_CAVE_DEMO_LEAVE_COUNT_KEY, 0]}")
            appendLine("- World-access violations: ${player[FIGHT_CAVE_DEMO_WORLD_ACCESS_COUNT_KEY, 0]}")
        }
        val file = File(player[FIGHT_CAVE_DEMO_CHECKLIST_PATH_KEY, checklistFile(sessionId(player)).absolutePath])
        file.parentFile.mkdirs()
        file.writeText(checklist)
    }

    private fun artifactPaths(player: Player): LinkedHashMap<String, Any?> = orderedMapOf(
        "session_log" to player[FIGHT_CAVE_DEMO_SESSION_LOG_PATH_KEY, sessionLogFile(sessionId(player)).absolutePath],
        "checklist" to player[FIGHT_CAVE_DEMO_CHECKLIST_PATH_KEY, checklistFile(sessionId(player)).absolutePath],
        "latest_manifest" to player[FIGHT_CAVE_DEMO_LAST_MANIFEST_PATH_KEY, ""],
    )

    private fun sessionId(player: Player): String = player[FIGHT_CAVE_DEMO_SESSION_ID_KEY, "${player.accountName}-${System.currentTimeMillis()}"]

    private fun rootDirectory(): File = File(Settings[FIGHT_CAVE_DEMO_ARTIFACT_ROOT_SETTING, FIGHT_CAVE_DEMO_ARTIFACT_ROOT_DEFAULT])

    private fun sessionLogFile(sessionId: String): File = rootDirectory().resolve("session_logs/$sessionId.jsonl")

    private fun manifestFile(sessionId: String, resetIndex: Int): File =
        rootDirectory().resolve("starter_state_manifests/${sessionId}_reset_${resetIndex.toString().padStart(3, '0')}.json")

    private fun checklistFile(sessionId: String): File = rootDirectory().resolve("validation_checklists/$sessionId.md")

    private fun spawnedNpcSnapshot(player: Player): List<LinkedHashMap<String, Any?>> =
        NPCs.at(player.tile.regionLevel)
            .map { npc ->
                orderedMapOf(
                    "id" to npc.id,
                    "tile" to tileMap(npc.tile),
                )
            }

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

    private fun orderedMapOf(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

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
}
