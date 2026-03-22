package content.area.karamja.tzhaar_city

import content.entity.player.effect.energy.MAX_RUN_ENERGY
import content.entity.player.effect.energy.energyPercent
import content.entity.player.effect.energy.runEnergy
import content.skill.prayer.getActivePrayerVarKey
import world.gregs.voidps.engine.GameLoop
import world.gregs.voidps.engine.client.variable.hasClock
import world.gregs.voidps.engine.entity.character.move.running
import world.gregs.voidps.engine.entity.character.npc.NPCs
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.inv.equipment
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.network.login.protocol.visual.update.player.EquipSlot
import world.gregs.voidps.type.Tile

private const val HEADLESS_OBSERVATION_SCHEMA_ID = "headless_observation_v1"
private const val HEADLESS_OBSERVATION_SCHEMA_VERSION = 1
private const val HEADLESS_OBSERVATION_COMPATIBILITY_POLICY = "v1_additive_only"
private const val FIGHT_CAVE_EPISODE_SEED_KEY = "episode_seed"
private const val FIGHT_CAVE_WAVE_KEY = "fight_cave_wave"
private const val FIGHT_CAVE_ROTATION_KEY = "fight_cave_rotation"
private const val FIGHT_CAVE_REMAINING_KEY = "fight_cave_remaining"

class FightCaveHeadedObservationBuilder(
    private val adapter: FightCaveBackendActionAdapter,
) {

    fun build(player: Player): LinkedHashMap<String, Any> {
        val activePrayerKey = player.getActivePrayerVarKey()
        val ammoItem = player.equipment[EquipSlot.Ammo.index]
        val visibleNpcs =
            adapter
                .visibleNpcTargets(player)
                .mapNotNull { target ->
                    val npc = NPCs.indexed(target.npcIndex) ?: return@mapNotNull null
                    val hitpointsCurrent = npc.levels.get(Skill.Constitution)
                    val hitpointsMax = npc.levels.getMax(Skill.Constitution)
                    linkedMapOf<String, Any>(
                        "visible_index" to target.visibleIndex,
                        "npc_index" to target.npcIndex,
                        "id" to target.id,
                        "tile" to tileMap(target.tile),
                        "hitpoints_current" to hitpointsCurrent,
                        "hitpoints_max" to hitpointsMax,
                        "hidden" to npc.hide,
                        "dead" to (npc.contains("dead") || hitpointsCurrent <= 0),
                        "under_attack" to npc.hasClock("under_attack"),
                        "jad_telegraph_state" to if (target.id == "tztok_jad") npc.jadTelegraphState.encoded else JadTelegraphState.Idle.encoded,
                    )
                }

        return linkedMapOf(
            "schema_id" to HEADLESS_OBSERVATION_SCHEMA_ID,
            "schema_version" to HEADLESS_OBSERVATION_SCHEMA_VERSION,
            "compatibility_policy" to HEADLESS_OBSERVATION_COMPATIBILITY_POLICY,
            "tick" to GameLoop.tick,
            "episode_seed" to player[FIGHT_CAVE_EPISODE_SEED_KEY, -1L],
            "player" to linkedMapOf(
                "tile" to tileMap(player.tile),
                "hitpoints_current" to player.levels.get(Skill.Constitution),
                "hitpoints_max" to player.levels.getMax(Skill.Constitution),
                "prayer_current" to player.levels.get(Skill.Prayer),
                "prayer_max" to player.levels.getMax(Skill.Prayer),
                "run_energy" to player.runEnergy,
                "run_energy_max" to MAX_RUN_ENERGY,
                "run_energy_percent" to player.energyPercent(),
                "running" to player.running,
                "protection_prayers" to linkedMapOf(
                    "protect_from_magic" to player.containsVarbit(activePrayerKey, "protect_from_magic"),
                    "protect_from_missiles" to player.containsVarbit(activePrayerKey, "protect_from_missiles"),
                    "protect_from_melee" to player.containsVarbit(activePrayerKey, "protect_from_melee"),
                ),
                "lockouts" to linkedMapOf(
                    "attack_locked" to player.hasClock("action_delay"),
                    "food_locked" to player.hasClock("food_delay"),
                    "drink_locked" to player.hasClock("drink_delay"),
                    "combo_locked" to player.hasClock("combo_delay"),
                    "busy_locked" to (player.contains("delay") || player.hasClock("stunned")),
                ),
                "consumables" to linkedMapOf(
                    "shark_count" to player.inventory.count("shark"),
                    "prayer_potion_dose_count" to prayerPotionDoseCount(player),
                    "ammo_id" to ammoItem.id.ifBlank { "" },
                    "ammo_count" to if (ammoItem.isEmpty()) 0 else ammoItem.amount,
                ),
            ),
            "wave" to linkedMapOf(
                "wave" to player[FIGHT_CAVE_WAVE_KEY, -1],
                "rotation" to player[FIGHT_CAVE_ROTATION_KEY, -1],
                "remaining" to player[FIGHT_CAVE_REMAINING_KEY, 0],
            ),
            "npcs" to visibleNpcs,
        )
    }

    private fun tileMap(tile: Tile): LinkedHashMap<String, Any> =
        linkedMapOf(
            "x" to tile.x,
            "y" to tile.y,
            "level" to tile.level,
        )

    private fun prayerPotionDoseCount(player: Player): Int {
        var doses = 0
        for (slot in player.inventory.indices) {
            val item = player.inventory[slot]
            doses +=
                when (item.id) {
                    "prayer_potion_4" -> 4 * item.amount
                    "prayer_potion_3" -> 3 * item.amount
                    "prayer_potion_2" -> 2 * item.amount
                    "prayer_potion_1" -> item.amount
                    else -> 0
                }
        }
        return doses
    }
}
