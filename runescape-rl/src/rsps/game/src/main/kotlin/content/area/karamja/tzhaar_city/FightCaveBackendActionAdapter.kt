package content.area.karamja.tzhaar_city

import content.entity.player.effect.energy.runEnergy
import content.quest.instance
import content.skill.prayer.getActivePrayerVarKey
import world.gregs.voidps.engine.GameLoop
import world.gregs.voidps.engine.client.instruction.handle.interactNpc
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.client.variable.hasClock
import world.gregs.voidps.engine.client.variable.start
import world.gregs.voidps.engine.entity.character.npc.NPC
import world.gregs.voidps.engine.entity.character.npc.NPCs
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.chat.ChatType
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.sound
import world.gregs.voidps.engine.entity.item.Item
import world.gregs.voidps.engine.entity.character.move.running
import world.gregs.voidps.engine.inv.Items
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.engine.inv.remove
import world.gregs.voidps.engine.inv.replace
import world.gregs.voidps.type.Tile

/**
 * Demo-only backend action adapter for the headed Fight Caves runtime.
 * Action ids and ordering semantics intentionally mirror the shared headless contract.
 */
enum class FightCaveBackendActionType(val id: Int, val wireName: String) {
    Wait(0, "wait"),
    WalkToTile(1, "walk_to_tile"),
    AttackVisibleNpc(2, "attack_visible_npc"),
    ToggleProtectionPrayer(3, "toggle_protection_prayer"),
    EatShark(4, "eat_shark"),
    DrinkPrayerPotion(5, "drink_prayer_potion"),
    ToggleRun(6, "toggle_run"),
    ;

    companion object {
        fun fromId(id: Int): FightCaveBackendActionType? = entries.firstOrNull { it.id == id }
        fun fromWireName(name: String): FightCaveBackendActionType? = entries.firstOrNull { it.wireName == name }
    }
}

enum class FightCaveBackendProtectionPrayer(val prayerId: String) {
    ProtectFromMagic("protect_from_magic"),
    ProtectFromMissiles("protect_from_missiles"),
    ProtectFromMelee("protect_from_melee"),
    ;

    companion object {
        fun fromPrayerId(prayerId: String): FightCaveBackendProtectionPrayer? = entries.firstOrNull { it.prayerId == prayerId }
    }
}

enum class FightCaveBackendActionRejectReason {
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

sealed interface FightCaveBackendAction {
    val type: FightCaveBackendActionType

    data object Wait : FightCaveBackendAction {
        override val type: FightCaveBackendActionType = FightCaveBackendActionType.Wait
    }

    data class WalkToTile(val tile: Tile) : FightCaveBackendAction {
        override val type: FightCaveBackendActionType = FightCaveBackendActionType.WalkToTile
    }

    data class AttackVisibleNpc(val visibleNpcIndex: Int) : FightCaveBackendAction {
        override val type: FightCaveBackendActionType = FightCaveBackendActionType.AttackVisibleNpc
    }

    data class ToggleProtectionPrayer(val prayer: FightCaveBackendProtectionPrayer) : FightCaveBackendAction {
        override val type: FightCaveBackendActionType = FightCaveBackendActionType.ToggleProtectionPrayer
    }

    data object EatShark : FightCaveBackendAction {
        override val type: FightCaveBackendActionType = FightCaveBackendActionType.EatShark
    }

    data object DrinkPrayerPotion : FightCaveBackendAction {
        override val type: FightCaveBackendActionType = FightCaveBackendActionType.DrinkPrayerPotion
    }

    data object ToggleRun : FightCaveBackendAction {
        override val type: FightCaveBackendActionType = FightCaveBackendActionType.ToggleRun
    }
}

data class FightCaveBackendActionResult(
    val actionType: FightCaveBackendActionType,
    val actionId: Int,
    val actionApplied: Boolean,
    val rejectionReason: FightCaveBackendActionRejectReason? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class FightCaveBackendVisibleNpcTarget(
    val visibleIndex: Int,
    val npcIndex: Int,
    val id: String,
    val tile: Tile,
)

class FightCaveBackendActionAdapter {
    fun apply(player: Player, action: FightCaveBackendAction): FightCaveBackendActionResult {
        val currentTick = GameLoop.tick
        val previousActionTick = player[LAST_ACTION_TICK_KEY, Int.MIN_VALUE]
        if (previousActionTick == currentTick) {
            return rejected(
                action = action,
                reason = FightCaveBackendActionRejectReason.AlreadyActedThisTick,
            )
        }
        player[LAST_ACTION_TICK_KEY] = currentTick

        return when (action) {
            FightCaveBackendAction.Wait -> applied(action)
            is FightCaveBackendAction.WalkToTile -> walk(player, action)
            is FightCaveBackendAction.AttackVisibleNpc -> attack(player, action)
            is FightCaveBackendAction.ToggleProtectionPrayer -> togglePrayer(player, action)
            FightCaveBackendAction.EatShark -> consume(player, SHARK_IDS, option = "Eat", lockClocks = listOf("food_delay"))
            FightCaveBackendAction.DrinkPrayerPotion -> consume(player, PRAYER_POTION_IDS, option = "Drink", lockClocks = listOf("drink_delay"))
            FightCaveBackendAction.ToggleRun -> toggleRun(player, action)
        }
    }

    fun visibleNpcTargets(player: Player): List<FightCaveBackendVisibleNpcTarget> =
        resolveVisibleNpcs(player)
            .sortedWith(VISIBLE_NPC_COMPARATOR)
            .mapIndexed { visibleIndex, npc ->
                FightCaveBackendVisibleNpcTarget(
                    visibleIndex = visibleIndex,
                    npcIndex = npc.index,
                    id = npc.id,
                    tile = npc.tile,
                )
            }

    private fun walk(player: Player, action: FightCaveBackendAction.WalkToTile): FightCaveBackendActionResult {
        if (player.tile == action.tile) {
            return rejected(action, FightCaveBackendActionRejectReason.NoMovementRequired)
        }
        player.walkTo(action.tile)
        return applied(
            action = action,
            metadata = mapOf(
                "target_tile" to action.tile.toString(),
                "movement_mode" to (player.mode::class.simpleName ?: "Unknown"),
                "movement_strategy" to "pathfinder",
            ),
        )
    }

    private fun attack(player: Player, action: FightCaveBackendAction.AttackVisibleNpc): FightCaveBackendActionResult {
        if (player.contains("delay") || player.hasClock("stunned")) {
            return rejected(action, FightCaveBackendActionRejectReason.PlayerBusy)
        }

        val visible = visibleNpcTargets(player)
        val index = action.visibleNpcIndex
        if (index !in visible.indices) {
            return rejected(
                action = action,
                reason = FightCaveBackendActionRejectReason.InvalidTargetIndex,
                metadata = mapOf("visible_npc_count" to visible.size.toString()),
            )
        }

        val targetMeta = visible[index]
        val target = NPCs.indexed(targetMeta.npcIndex)
        if (target == null || target.index == -1 || target.hide || target.contains("dead")) {
            return rejected(
                action = action,
                reason = FightCaveBackendActionRejectReason.TargetNotVisible,
                metadata = mapOf("visible_npc_index" to index.toString()),
            )
        }

        player.interactNpc(target, "Attack")
        val destination = player.steps.destination
        val pathingRequired = destination != Tile.EMPTY && destination != player.tile
        val attackResolution = if (pathingRequired) "moved_or_pathed_toward_target" else "attacked_immediately_in_place"
        return applied(
            action = action,
            metadata = mapOf(
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

    private fun togglePrayer(player: Player, action: FightCaveBackendAction.ToggleProtectionPrayer): FightCaveBackendActionResult {
        val key = player.getActivePrayerVarKey()
        val prayer = action.prayer.prayerId

        if (player.containsVarbit(key, prayer)) {
            player.removeVarbit(key, prayer)
            return applied(action, metadata = mapOf("prayer" to prayer, "active" to "false"))
        }

        if (player.levels.get(Skill.Prayer) <= 0) {
            return rejected(action, FightCaveBackendActionRejectReason.PrayerPointsDepleted)
        }

        for (name in PROTECTION_PRAYERS) {
            if (name != prayer) {
                player.removeVarbit(key, name, refresh = false)
            }
        }
        player.addVarbit(key, prayer)

        return applied(action, metadata = mapOf("prayer" to prayer, "active" to "true"))
    }

    private fun consume(player: Player, itemIds: Set<String>, option: String, lockClocks: List<String>): FightCaveBackendActionResult {
        val action = if (option == "Eat") FightCaveBackendAction.EatShark else FightCaveBackendAction.DrinkPrayerPotion
        val activeLock = lockClocks.firstOrNull(player::hasClock)
        if (activeLock != null) {
            return rejected(
                action = action,
                reason = FightCaveBackendActionRejectReason.ConsumptionLocked,
                metadata = mapOf("lock" to activeLock),
            )
        }

        val slot = player.inventory.indices.firstOrNull { player.inventory[it].id in itemIds }
        if (slot == null) {
            return rejected(action, FightCaveBackendActionRejectReason.MissingConsumable)
        }

        val before = player.inventory[slot]
        val consumed = consumeItem(player, before, slot, option)
        val after = player.inventory[slot]
        if (!consumed && before == after && lockClocks.none(player::hasClock)) {
            return rejected(action, FightCaveBackendActionRejectReason.ConsumptionLocked)
        }

        return applied(
            action = action,
            metadata = mapOf(
                "consumed_item" to before.id,
                "slot" to slot.toString(),
            ),
        )
    }

    private fun consumeItem(player: Player, item: Item, slot: Int, option: String): Boolean {
        if (!item.def.contains("heals") && !item.def.contains("excess")) {
            return false
        }
        val drink = option == "Drink"
        val combo = item.def.contains("combo")
        val delay = when {
            combo -> "combo_delay"
            drink -> "drink_delay"
            else -> "food_delay"
        }
        val ticks = when {
            combo -> 1
            drink -> 2
            else -> 3
        }
        if (player.hasClock(delay)) {
            return false
        }
        player.start(delay, ticks)
        if (!Items.consumable(player, item)) {
            return false
        }
        val replacement = item.def["excess", ""]
        val smash = player["vial_smashing", false] && replacement == "vial"
        if (replacement.isEmpty() || smash) {
            player.inventory.remove(slot, item.id)
        } else {
            player.inventory.replace(slot, item.id, replacement)
        }
        player.anim("eat_drink")
        val message = item.def["eat_message", ""]
        if (message.isNotEmpty()) {
            player.message(message, ChatType.Filter)
        } else {
            player.message("You ${if (drink) "drink" else "eat"} the ${item.def.name.lowercase()}.", ChatType.Filter)
        }
        player.sound(if (drink) "drink" else "eat")
        Items.consume(player, item, slot)
        if (smash) {
            player.message("You quickly smash the empty vial using the trick a Barbarian taught you.", ChatType.Filter)
        }
        return true
    }

    private fun toggleRun(player: Player, action: FightCaveBackendAction): FightCaveBackendActionResult {
        val enablingRun = !player.running
        if (enablingRun && player.runEnergy <= 0) {
            return rejected(action, FightCaveBackendActionRejectReason.InsufficientRunEnergy)
        }
        player.running = enablingRun
        player["movement_temp"] = if (player.running) "run" else "walk"
        return applied(action, metadata = mapOf("running" to player.running.toString()))
    }

    private fun resolveVisibleNpcs(player: Player): List<NPC> {
        val viewportNpcs = sourceNpcsFromViewport(player)
        val source = if (viewportNpcs.isNotEmpty()) viewportNpcs else sourceNpcsFromInstanceOrRegion(player)
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

    private fun applied(action: FightCaveBackendAction, metadata: Map<String, String> = emptyMap()): FightCaveBackendActionResult =
        FightCaveBackendActionResult(
            actionType = action.type,
            actionId = action.type.id,
            actionApplied = true,
            rejectionReason = null,
            metadata = mapOf("action_applied" to "true") + metadata,
        )

    private fun rejected(
        action: FightCaveBackendAction,
        reason: FightCaveBackendActionRejectReason,
        metadata: Map<String, String> = emptyMap(),
    ): FightCaveBackendActionResult =
        FightCaveBackendActionResult(
            actionType = action.type,
            actionId = action.type.id,
            actionApplied = false,
            rejectionReason = reason,
            metadata = mapOf("action_applied" to "false") + metadata,
        )

    companion object {
        private const val DEFAULT_VIEW_RADIUS = 15
        private const val LAST_ACTION_TICK_KEY = "fight_cave_demo_backend_last_action_tick"

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
