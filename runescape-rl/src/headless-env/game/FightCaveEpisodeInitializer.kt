import content.area.karamja.tzhaar_city.TzhaarFightCave
import content.entity.combat.damageDealers
import content.entity.player.effect.energy.MAX_RUN_ENERGY
import content.entity.player.effect.energy.runEnergy
import headless.fast.FIGHT_CAVE_DEFAULT_EQUIPMENT_TEMPLATE
import headless.fast.FIGHT_CAVE_DEFAULT_INVENTORY_TEMPLATE
import headless.fast.FIGHT_CAVE_EPISODE_SEED_KEY
import headless.fast.FIGHT_CAVE_FIXED_LEVELS
import headless.fast.FIGHT_CAVE_RESET_CLOCKS
import headless.fast.FIGHT_CAVE_RESET_VARIABLES
import headless.fast.FIGHT_CAVE_REMAINING_KEY
import headless.fast.FIGHT_CAVE_ROTATION_KEY
import headless.fast.FIGHT_CAVE_WAVE_KEY
import headless.fast.FightCaveEquipmentSlot
import content.quest.clearInstance
import content.quest.instance
import content.quest.instanceOffset
import content.quest.smallInstance
import content.skill.prayer.PrayerConfigs
import sim.engine.client.variable.stop
import sim.engine.data.ConfigFiles
import sim.engine.entity.character.mode.EmptyMode
import sim.engine.entity.character.move.running
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level
import sim.engine.entity.item.Item
import sim.engine.inv.equipment
import sim.engine.inv.inventory
import sim.engine.inv.transact.operation.AddItem.add
import sim.engine.inv.transact.operation.ClearItem.clear
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.types.Tile
import sim.types.setSeededRandom

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

class FightCaveEpisodeInitializer(
    private val fightCave: TzhaarFightCave,
    private val configFiles: ConfigFiles,
) {

    fun reset(player: Player, config: FightCaveEpisodeConfig): FightCaveEpisodeState {
        require(config.startWave in 1..63) { "Fight Caves start wave must be in range 1..63, got ${config.startWave}." }
        require(config.ammo > 0) { "Ammo amount must be > 0, got ${config.ammo}." }
        require(config.prayerPotions >= 0) { "Prayer potion count cannot be negative, got ${config.prayerPotions}." }
        require(config.sharks >= 0) { "Shark count cannot be negative, got ${config.sharks}." }

        setSeededRandom(config.seed, trackCalls = true)
        player[FIGHT_CAVE_EPISODE_SEED_KEY] = config.seed

        fightCave.ensureWavesLoaded(configFiles)

        resetTransientState(player)
        resetFightCaveState(player)
        resetPlayerStatsAndResources(player)
        resetPlayerLoadout(player, config)
        resetFightCaveInstance(player, config)

        return FightCaveEpisodeState(
            seed = config.seed,
            wave = player[FIGHT_CAVE_WAVE_KEY, -1],
            rotation = player[FIGHT_CAVE_ROTATION_KEY, -1],
            remaining = player[FIGHT_CAVE_REMAINING_KEY, 0],
            instanceId = checkNotNull(player.instance()).id,
            playerTile = player.tile,
        )
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

        for (clock in FIGHT_CAVE_RESET_CLOCKS) {
            player.stop(clock)
        }

        player["logged_out"] = false
    }

    private fun resetFightCaveState(player: Player) {
        // Clear wave/remaining variables FIRST so deferred npcDespawn events
        // from clearPreviousInstance see wave=-1 and are suppressed by the guard.
        for (variable in FIGHT_CAVE_RESET_VARIABLES) {
            player.clear(variable)
        }

        clearPreviousInstance(player)

        player["fight_caves_logout_warning"] = false
        player["headless_episode"] = true

        player[PrayerConfigs.PRAYERS] = ""
        player[PrayerConfigs.USING_QUICK_PRAYERS] = false
        player[PrayerConfigs.SELECTING_QUICK_PRAYERS] = false
        player.clear(PrayerConfigs.ACTIVE_PRAYERS)
        player.clear(PrayerConfigs.ACTIVE_CURSES)
        player.clear(PrayerConfigs.QUICK_PRAYERS)
        player.clear(PrayerConfigs.QUICK_CURSES)
        player.clear(PrayerConfigs.TEMP_QUICK_PRAYERS)
        player.clear("prayer_drain_counter")
    }

    private fun clearPreviousInstance(player: Player) {
        val previous = player.instance() ?: return
        repeat(4) { level ->
            NPCs.clear(previous.toLevel(level))
        }
        // Set remaining to a sentinel AFTER NPCs are queued for removal
        // but BEFORE NPCs.run() fires despawn events. This ensures the
        // npcDespawn handler's dec() always returns > 0, preventing wave advancement.
        player[FIGHT_CAVE_REMAINING_KEY] = 9999
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
        player["movement_temp"] = "run"
        player["skip_level_up"] = true
        // Step 28: Enable auto-retaliation so the player attacks back when NPCs aggress.
        player["auto_retaliate"] = true
        // Set combat state variables required for the combat swing cycle.
        // CombatStyles script isn't loaded in headless mode, so these must
        // be set explicitly. Without them, fightStyle/attackRange/weapon are
        // empty and the combat swing handler never fires.
        player["combat_style"] = "accurate"   // attackType for Ranged
        player["attack_range"] = 7            // crossbow attack range
        player["weapon"] = Item("rune_crossbow")  // weapon reference for swing dispatch
    }

    private fun resetPlayerLoadout(player: Player, config: FightCaveEpisodeConfig) {
        val equipmentReset =
            player.equipment.transaction {
                clear()
                for (entry in FIGHT_CAVE_DEFAULT_EQUIPMENT_TEMPLATE) {
                    val amount = if (entry.slot == FightCaveEquipmentSlot.Ammo) config.ammo else entry.amount
                    set(equipSlotIndex(entry.slot), Item(entry.itemId, amount))
                }
            }
        check(equipmentReset) { "Failed to reset headless episode equipment loadout." }

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
        check(inventoryReset) { "Failed to reset headless episode consumable inventory." }
    }

    private fun resetFightCaveInstance(player: Player, config: FightCaveEpisodeConfig) {
        player.smallInstance(fightCave.region, levels = 3)
        val offset = player.instanceOffset()
        player.tele(fightCave.entrance.add(offset))
        player.walkTo(fightCave.centre.add(offset))
        fightCave.startWave(player, config.startWave, start = false)

        // Flush deferred NPC despawn events from previous instance cleanup,
        // then re-assert the correct wave state. Without this, stale despawns
        // fire on the next tick and corrupt the wave counter.
        NPCs.run()
        fightCave.startWave(player, config.startWave, start = false)
    }

    private fun equipSlotIndex(slot: FightCaveEquipmentSlot): Int =
        when (slot) {
            FightCaveEquipmentSlot.Hat -> EquipSlot.Hat.index
            FightCaveEquipmentSlot.Weapon -> EquipSlot.Weapon.index
            FightCaveEquipmentSlot.Chest -> EquipSlot.Chest.index
            FightCaveEquipmentSlot.Legs -> EquipSlot.Legs.index
            FightCaveEquipmentSlot.Hands -> EquipSlot.Hands.index
            FightCaveEquipmentSlot.Feet -> EquipSlot.Feet.index
            FightCaveEquipmentSlot.Ammo -> EquipSlot.Ammo.index
        }
}
