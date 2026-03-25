package content.area.karamja.tzhaar_city

import content.entity.combat.attacker
import content.entity.combat.attackers
import content.entity.combat.damageDealers
import content.entity.combat.dead
import content.entity.combat.target
import content.entity.player.effect.energy.MAX_RUN_ENERGY
import content.entity.player.effect.energy.runEnergy
import content.quest.clearInstance
import content.quest.instance
import content.quest.instanceOffset
import content.quest.smallInstance
import content.skill.prayer.PrayerConfigs
import world.gregs.voidps.engine.client.instruction.handle.interactPlayer
import world.gregs.voidps.engine.client.variable.stop
import world.gregs.voidps.engine.entity.character.mode.EmptyMode
import world.gregs.voidps.engine.entity.character.move.running
import world.gregs.voidps.engine.entity.character.move.tele
import world.gregs.voidps.engine.entity.character.npc.NPCs
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.level.Level
import world.gregs.voidps.engine.entity.item.Item
import world.gregs.voidps.engine.event.AuditLog
import world.gregs.voidps.engine.inv.equipment
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.engine.inv.transact.operation.AddItem.add
import world.gregs.voidps.engine.inv.transact.operation.ClearItem.clear
import world.gregs.voidps.engine.queue.softQueue
import world.gregs.voidps.network.login.protocol.visual.update.player.EquipSlot
import world.gregs.voidps.type.Tile
import world.gregs.voidps.type.setRandom
import kotlin.random.Random

data class FightCaveEpisodeConfig(
    val seed: Long,
    val startWave: Int = 1,
    val ammo: Int = 1000,
    val prayerPotions: Int = 8,
    val sharks: Int = 20,
)

data class FightCaveEpisodeState(
    val seed: Long,
    val wave: Int,
    val rotation: Int,
    val remaining: Int,
    val instanceId: Int,
    val playerTile: Tile,
)

private data class FightCaveEquipmentTemplate(
    val slot: Int,
    val itemId: String,
    val amount: Int = 1,
)

private data class FightCaveInventoryTemplate(
    val itemId: String,
    val amount: Int,
)

private const val FIGHT_CAVE_EPISODE_SEED_KEY = "episode_seed"
private const val FIGHT_CAVE_WAVE_KEY = "fight_cave_wave"
private const val FIGHT_CAVE_ROTATION_KEY = "fight_cave_rotation"
private const val FIGHT_CAVE_REMAINING_KEY = "fight_cave_remaining"
private const val FIGHT_CAVE_RESET_AGGRESSION_FOLLOWUP_DELAY_TICKS = 2

private val FIGHT_CAVE_RESET_CLOCKS =
    listOf(
        "delay",
        "movement_delay",
        "food_delay",
        "drink_delay",
        "combo_delay",
        "action_delay",
        "under_attack",
        "fight_cave_cooldown",
    )

private val FIGHT_CAVE_FIXED_LEVELS =
    mapOf(
        Skill.Attack to 1,
        Skill.Strength to 1,
        Skill.Defence to 70,
        Skill.Constitution to 700,
        Skill.Ranged to 70,
        Skill.Prayer to 43,
        Skill.Magic to 1,
    )

private val FIGHT_CAVE_RESET_VARIABLES =
    listOf(
        FIGHT_CAVE_WAVE_KEY,
        FIGHT_CAVE_ROTATION_KEY,
        FIGHT_CAVE_REMAINING_KEY,
        "fight_cave_start_time",
        "healed",
    )

private val FIGHT_CAVE_DEFAULT_EQUIPMENT_TEMPLATE =
    listOf(
        FightCaveEquipmentTemplate(EquipSlot.Hat.index, "coif"),
        FightCaveEquipmentTemplate(EquipSlot.Weapon.index, "rune_crossbow"),
        FightCaveEquipmentTemplate(EquipSlot.Chest.index, "black_dragonhide_body"),
        FightCaveEquipmentTemplate(EquipSlot.Legs.index, "black_dragonhide_chaps"),
        FightCaveEquipmentTemplate(EquipSlot.Hands.index, "black_dragonhide_vambraces"),
        FightCaveEquipmentTemplate(EquipSlot.Feet.index, "snakeskin_boots"),
        FightCaveEquipmentTemplate(EquipSlot.Ammo.index, "adamant_bolts"),
    )

private val FIGHT_CAVE_DEFAULT_INVENTORY_TEMPLATE =
    listOf(
        FightCaveInventoryTemplate("prayer_potion_4", 8),
        FightCaveInventoryTemplate("shark", 20),
    )

class FightCaveEpisodeInitializer(
    private val fightCave: TzhaarFightCave,
) {

    fun reset(player: Player, config: FightCaveEpisodeConfig): FightCaveEpisodeState {
        require(config.startWave in 1..63) { "Fight Caves start wave must be in range 1..63, got ${config.startWave}." }
        require(config.ammo > 0) { "Ammo amount must be > 0, got ${config.ammo}." }
        require(config.prayerPotions >= 0) { "Prayer potion count cannot be negative, got ${config.prayerPotions}." }
        require(config.sharks >= 0) { "Shark count cannot be negative, got ${config.sharks}." }

        setRandom(Random(config.seed))
        player[FIGHT_CAVE_EPISODE_SEED_KEY] = config.seed

        resetTransientState(player)
        resetFightCaveState(player)
        resetPlayerStatsAndResources(player)
        resetPlayerLoadout(player, config)
        resetFightCaveInstance(player, config)

        val state =
            FightCaveEpisodeState(
                seed = config.seed,
                wave = player[FIGHT_CAVE_WAVE_KEY, -1],
                rotation = player[FIGHT_CAVE_ROTATION_KEY, -1],
                remaining = player[FIGHT_CAVE_REMAINING_KEY, 0],
                instanceId = checkNotNull(player.instance()).id,
                playerTile = player.tile,
            )
        AuditLog.event(player, "fight_cave_episode_reset", state.seed, state.wave, state.rotation, state.remaining, state.instanceId)
        return state
    }

    private fun resetTransientState(player: Player) {
        player.queue.clear()
        player.softTimers.stopAll()
        player.timers.stopAll()
        player.mode = EmptyMode
        player.steps.clear()
        player.clearFace()
        player.clearWatch()
        player.clearAnim()
        player.clearGfx()
        player.target = null
        player.attacker = null
        player.attackers.clear()
        player.damageDealers.clear()
        player.dead = false

        for (clock in FIGHT_CAVE_RESET_CLOCKS) {
            player.stop(clock)
        }

        player["logged_out"] = false
    }

    private fun resetFightCaveState(player: Player) {
        clearPreviousInstance(player)

        for (variable in FIGHT_CAVE_RESET_VARIABLES) {
            player.clear(variable)
        }

        player["fight_caves_logout_warning"] = false
        player["fight_cave_demo_entry_pending"] = false
        player["fight_cave_demo_episode"] = player["fight_cave_demo_profile", false]
        player["task_disable_popups"] = true
        player["task_dont_show_again"] = true
        player["task_popup"] = 0
        player["task_previous_popup"] = 0
        player["task_area"] = "empty"

        player[PrayerConfigs.PRAYERS] = PrayerConfigs.BOOK_PRAYERS
        player.clear(PrayerConfigs.USING_QUICK_PRAYERS)
        player.clear(PrayerConfigs.SELECTING_QUICK_PRAYERS)
        player.clear(PrayerConfigs.ACTIVE_PRAYERS)
        player.clear(PrayerConfigs.ACTIVE_CURSES)
        player.clear(PrayerConfigs.QUICK_PRAYERS)
        player.clear(PrayerConfigs.QUICK_CURSES)
        player.clear(PrayerConfigs.TEMP_QUICK_PRAYERS)
        player.clear("prayer_drain_counter")
        player.sendVariable(PrayerConfigs.PRAYERS)
        player.sendVariable(PrayerConfigs.USING_QUICK_PRAYERS)
        player.sendVariable(PrayerConfigs.SELECTING_QUICK_PRAYERS)
        player.sendVariable(PrayerConfigs.ACTIVE_PRAYERS)
        player.sendVariable(PrayerConfigs.ACTIVE_CURSES)
    }

    private fun clearPreviousInstance(player: Player) {
        val previous = player.instance() ?: return
        repeat(4) { level ->
            NPCs.clear(previous.toLevel(level))
        }
        NPCs.run()
        player.clearInstance()
    }

    private fun resetPlayerStatsAndResources(player: Player) {
        for (skill in Skill.all) {
            player.experience.removeBlock(skill)
            val level = FIGHT_CAVE_FIXED_LEVELS[skill] ?: 1
            player.experience.set(skill, Level.experience(skill, level))
            player.levels.set(skill, level)
            player.experience.addBlock(skill)
        }

        player.runEnergy = MAX_RUN_ENERGY
        player.running = true
        player["auto_retaliate"] = true
        player["movement_temp"] = "run"
        player["skip_level_up"] = true
    }

    private fun resetPlayerLoadout(player: Player, config: FightCaveEpisodeConfig) {
        val equipmentReset =
            player.equipment.transaction {
                clear()
                for (entry in FIGHT_CAVE_DEFAULT_EQUIPMENT_TEMPLATE) {
                    val amount = if (entry.slot == EquipSlot.Ammo.index) config.ammo else entry.amount
                    set(entry.slot, Item(entry.itemId, amount))
                }
            }
        check(equipmentReset) { "Failed to reset Fight Caves demo equipment loadout." }

        val inventoryReset =
            player.inventory.transaction {
                clear()
                for (entry in FIGHT_CAVE_DEFAULT_INVENTORY_TEMPLATE) {
                    val amount =
                        when (entry.itemId) {
                            "prayer_potion_4" -> config.prayerPotions
                            "shark" -> config.sharks
                            else -> entry.amount
                        }
                    add(entry.itemId, amount)
                }
            }
        check(inventoryReset) { "Failed to reset Fight Caves demo consumable inventory." }
    }

    private fun resetFightCaveInstance(player: Player, config: FightCaveEpisodeConfig) {
        player.smallInstance(fightCave.region, levels = 3)
        val offset = player.instanceOffset()
        player.tele(fightCave.centre.add(offset))
        fightCave.startWave(player, config.startWave, start = false)
        player.softQueue("fight_cave_demo_reset_wave_aggression", FIGHT_CAVE_RESET_AGGRESSION_FOLLOWUP_DELAY_TICKS) {
            for (npc in NPCs.at(player.tile.regionLevel)) {
                npc["in_multi_combat"] = true
                npc.interactPlayer(player, "Attack")
            }
        }
    }
}
