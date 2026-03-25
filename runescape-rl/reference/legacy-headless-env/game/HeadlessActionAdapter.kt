import content.entity.player.effect.energy.runEnergy
import content.quest.instance
import content.skill.constitution.Eating
import content.skill.prayer.getActivePrayerVarKey
import sim.engine.GameLoop
import sim.engine.Script
import sim.engine.client.instruction.handle.interactNpc
import sim.engine.client.ui.ItemOption
import sim.engine.client.variable.hasClock
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.move.running
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.inv.inventory
import sim.types.Tile

/**
 * Stable action identifiers for the headless API. IDs are append-only.
 */
enum class HeadlessActionType(val id: Int) {
    Wait(0),
    WalkToTile(1),
    AttackVisibleNpc(2),
    ToggleProtectionPrayer(3),
    EatShark(4),
    DrinkPrayerPotion(5),
    ToggleRun(6),
}

enum class HeadlessProtectionPrayer(val prayerId: String) {
    ProtectFromMagic("protect_from_magic"),
    ProtectFromMissiles("protect_from_missiles"),
    ProtectFromMelee("protect_from_melee"),
}

enum class HeadlessActionRejectReason {
    AlreadyActedThisTick,
    InvalidTargetIndex,
    TargetNotVisible,
    PlayerBusy,
    MissingConsumable,
    ConsumptionLocked,
    PrayerPointsDepleted,
    InsufficientRunEnergy,
    NoMovementRequired,
}

sealed interface HeadlessAction {
    val type: HeadlessActionType

    data object Wait : HeadlessAction {
        override val type: HeadlessActionType = HeadlessActionType.Wait
    }

    data class WalkToTile(val tile: Tile) : HeadlessAction {
        override val type: HeadlessActionType = HeadlessActionType.WalkToTile
    }

    data class AttackVisibleNpc(val visibleNpcIndex: Int) : HeadlessAction {
        override val type: HeadlessActionType = HeadlessActionType.AttackVisibleNpc
    }

    data class ToggleProtectionPrayer(val prayer: HeadlessProtectionPrayer) : HeadlessAction {
        override val type: HeadlessActionType = HeadlessActionType.ToggleProtectionPrayer
    }

    data object EatShark : HeadlessAction {
        override val type: HeadlessActionType = HeadlessActionType.EatShark
    }

    data object DrinkPrayerPotion : HeadlessAction {
        override val type: HeadlessActionType = HeadlessActionType.DrinkPrayerPotion
    }

    data object ToggleRun : HeadlessAction {
        override val type: HeadlessActionType = HeadlessActionType.ToggleRun
    }
}

data class HeadlessActionResult(
    val actionType: HeadlessActionType,
    val actionId: Int,
    val actionApplied: Boolean,
    val rejectionReason: HeadlessActionRejectReason? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class HeadlessVisibleNpcTarget(
    val visibleIndex: Int,
    val npcIndex: Int,
    val id: String,
    val tile: Tile,
)

class HeadlessActionAdapter(
    loadedScripts: List<Script>,
) {

    private val eating =
        loadedScripts.filterIsInstance<Eating>().singleOrNull()
            ?: error("Headless runtime did not load Eating script required by action adapter.")

    fun apply(player: Player, action: HeadlessAction): HeadlessActionResult {
        val currentTick = GameLoop.tick
        val previousActionTick = player[LAST_ACTION_TICK_KEY, Int.MIN_VALUE]
        if (previousActionTick == currentTick) {
            return rejected(
                action = action,
                reason = HeadlessActionRejectReason.AlreadyActedThisTick,
            )
        }
        player[LAST_ACTION_TICK_KEY] = currentTick

        return when (action) {
            HeadlessAction.Wait -> applied(action)
            is HeadlessAction.WalkToTile -> walk(player, action)
            is HeadlessAction.AttackVisibleNpc -> attack(player, action)
            is HeadlessAction.ToggleProtectionPrayer -> togglePrayer(player, action)
            HeadlessAction.EatShark -> consume(player, itemIds = SHARK_IDS, option = "Eat", lockClocks = listOf("food_delay"))
            HeadlessAction.DrinkPrayerPotion -> consume(player, itemIds = PRAYER_POTION_IDS, option = "Drink", lockClocks = listOf("drink_delay"))
            HeadlessAction.ToggleRun -> toggleRun(player, action)
        }
    }

    fun visibleNpcTargets(player: Player): List<HeadlessVisibleNpcTarget> =
        resolveVisibleNpcs(player)
            .sortedWith(VISIBLE_NPC_COMPARATOR)
            .mapIndexed { visibleIndex, npc ->
                HeadlessVisibleNpcTarget(
                    visibleIndex = visibleIndex,
                    npcIndex = npc.index,
                    id = npc.id,
                    tile = npc.tile,
                )
            }

    private fun walk(player: Player, action: HeadlessAction.WalkToTile): HeadlessActionResult {
        if (player.tile == action.tile) {
            return rejected(action, HeadlessActionRejectReason.NoMovementRequired)
        }
        player.walkTo(action.tile)
        return applied(
            action = action,
            metadata =
                mapOf(
                    "target_tile" to action.tile.toString(),
                    "movement_mode" to player.mode::class.simpleName.orEmpty(),
                    "movement_strategy" to "pathfinder",
                ),
        )
    }

    private fun attack(player: Player, action: HeadlessAction.AttackVisibleNpc): HeadlessActionResult {
        if (player.contains("delay") || player.hasClock("stunned")) {
            return rejected(action, HeadlessActionRejectReason.PlayerBusy)
        }

        val visible = visibleNpcTargets(player)
        val index = action.visibleNpcIndex
        if (index !in visible.indices) {
            return rejected(
                action = action,
                reason = HeadlessActionRejectReason.InvalidTargetIndex,
                metadata = mapOf("visible_npc_count" to visible.size.toString()),
            )
        }

        val targetMeta = visible[index]
        val target = NPCs.indexed(targetMeta.npcIndex)
        if (target == null || target.index == -1 || target.hide || target.contains("dead")) {
            return rejected(
                action = action,
                reason = HeadlessActionRejectReason.TargetNotVisible,
                metadata = mapOf("visible_npc_index" to index.toString()),
            )
        }

        player.interactNpc(target, "Attack")
        val destination = player.steps.destination
        val pathingRequired = destination != Tile.EMPTY && destination != player.tile
        val attackResolution = if (pathingRequired) "moved_or_pathed_toward_target" else "attacked_immediately_in_place"
        return applied(
            action = action,
            metadata =
                mapOf(
                    "target_npc_index" to target.index.toString(),
                    "target_npc_id" to target.id,
                    "target_npc_tile" to target.tile.toString(),
                    "visible_npc_index" to index.toString(),
                    "attack_resolution" to attackResolution,
                    "interaction_mode" to (player.mode::class.simpleName ?: "Unknown"),
                    "destination_tile" to destination.toString(),
                    "pathing_required" to pathingRequired.toString(),
                ),
        )
    }

    private fun togglePrayer(player: Player, action: HeadlessAction.ToggleProtectionPrayer): HeadlessActionResult {
        val key = player.getActivePrayerVarKey()
        val prayer = action.prayer.prayerId

        if (player.containsVarbit(key, prayer)) {
            player.removeVarbit(key, prayer)
            return applied(
                action = action,
                metadata = mapOf("prayer" to prayer, "active" to "false"),
            )
        }

        if (player.levels.get(Skill.Prayer) <= 0) {
            return rejected(action, HeadlessActionRejectReason.PrayerPointsDepleted)
        }

        for (name in PROTECTION_PRAYERS) {
            if (name != prayer) {
                player.removeVarbit(key, name, refresh = false)
            }
        }
        player.addVarbit(key, prayer)

        return applied(
            action = action,
            metadata = mapOf("prayer" to prayer, "active" to "true"),
        )
    }

    private fun consume(player: Player, itemIds: Set<String>, option: String, lockClocks: List<String>): HeadlessActionResult {
        val action = if (option == "Eat") HeadlessAction.EatShark else HeadlessAction.DrinkPrayerPotion

        val activeLock = lockClocks.firstOrNull(player::hasClock)
        if (activeLock != null) {
            return rejected(
                action = action,
                reason = HeadlessActionRejectReason.ConsumptionLocked,
                metadata = mapOf("lock" to activeLock),
            )
        }

        val slot = player.inventory.indices.firstOrNull { player.inventory[it].id in itemIds }
        if (slot == null) {
            return rejected(action, HeadlessActionRejectReason.MissingConsumable)
        }

        val before = player.inventory[slot]
        eating.consume(player, ItemOption(before, slot, "inventory", option))
        val after = player.inventory[slot]

        val consumed = before != after || lockClocks.any(player::hasClock)
        if (!consumed) {
            return rejected(action, HeadlessActionRejectReason.ConsumptionLocked)
        }

        return applied(
            action = action,
            metadata =
                mapOf(
                    "consumed_item" to before.id,
                    "slot" to slot.toString(),
                ),
        )
    }

    private fun toggleRun(player: Player, action: HeadlessAction): HeadlessActionResult {
        val enablingRun = !player.running
        if (enablingRun && player.runEnergy <= 0) {
            return rejected(action, HeadlessActionRejectReason.InsufficientRunEnergy)
        }
        player.running = enablingRun
        player["movement_temp"] = if (player.running) "run" else "walk"
        return applied(
            action = action,
            metadata = mapOf("running" to player.running.toString()),
        )
    }

    private fun resolveVisibleNpcs(player: Player): List<NPC> {
        val viewportNpcs = sourceNpcsFromViewport(player)

        val source =
            if (viewportNpcs.isNotEmpty()) {
                viewportNpcs
            } else {
                sourceNpcsFromInstanceOrRegion(player)
            }

        val radius = player.viewport?.radius ?: DEFAULT_VIEW_RADIUS
        return source.filter { npc ->
            npc.index != -1 &&
                !npc.hide &&
                !npc.contains("dead") &&
                npc.tile.within(player.tile, radius)
        }
    }
    private fun sourceNpcsFromViewport(player: Player): List<NPC> {
        val viewport = player.viewport ?: return emptyList()
        if (viewport.npcs.isEmpty()) {
            return emptyList()
        }
        val list = ArrayList<NPC>(viewport.npcs.size)
        val iterator = viewport.npcs.iterator()
        while (iterator.hasNext()) {
            val npc = NPCs.indexed(iterator.nextInt()) ?: continue
            list.add(npc)
        }
        return list
    }
    private fun sourceNpcsFromInstanceOrRegion(player: Player): List<NPC> {
        val instance = player.instance()
        return if (instance != null) {
            buildList {
                for (level in 0..3) {
                    addAll(NPCs.at(instance.toLevel(level)))
                }
            }
        } else {
            NPCs.at(player.tile.regionLevel)
        }
    }

    private fun applied(action: HeadlessAction, metadata: Map<String, String> = emptyMap()): HeadlessActionResult =
        HeadlessActionResult(
            actionType = action.type,
            actionId = action.type.id,
            actionApplied = true,
            rejectionReason = null,
            metadata = mapOf("action_applied" to "true") + metadata,
        )

    private fun rejected(action: HeadlessAction, reason: HeadlessActionRejectReason, metadata: Map<String, String> = emptyMap()): HeadlessActionResult =
        HeadlessActionResult(
            actionType = action.type,
            actionId = action.type.id,
            actionApplied = false,
            rejectionReason = reason,
            metadata = mapOf("action_applied" to "false") + metadata,
        )

    companion object {
        private const val DEFAULT_VIEW_RADIUS = 15
        private const val LAST_ACTION_TICK_KEY = "headless_last_action_tick"

        private val SHARK_IDS = setOf("shark")
        private val PRAYER_POTION_IDS = setOf("prayer_potion_4", "prayer_potion_3", "prayer_potion_2", "prayer_potion_1")
        private val PROTECTION_PRAYERS = setOf("protect_from_magic", "protect_from_missiles", "protect_from_melee")

        private val VISIBLE_NPC_COMPARATOR =
            compareBy<NPC>(
                { it.tile.level },
                { it.tile.x },
                { it.tile.y },
                { it.id },
                { it.index },
            )
    }
}



