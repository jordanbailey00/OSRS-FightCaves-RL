import content.area.karamja.tzhaar_city.JadTelegraphState
import content.area.karamja.tzhaar_city.jadTelegraphState
import content.entity.player.effect.energy.MAX_RUN_ENERGY
import content.entity.player.effect.energy.runEnergy
import headless.fast.FIGHT_CAVE_EPISODE_SEED_KEY
import headless.fast.FIGHT_CAVE_REMAINING_KEY
import headless.fast.FIGHT_CAVE_ROTATION_KEY
import headless.fast.FIGHT_CAVE_WAVE_KEY
import headless.fast.fightCavePrayerPotionDoseCount
import content.skill.prayer.getActivePrayerVarKey
import sim.engine.GameLoop
import sim.engine.client.variable.hasClock
import sim.engine.entity.character.move.running
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.inv.equipment
import sim.engine.inv.inventory
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.types.Tile

const val HEADLESS_OBSERVATION_SCHEMA_ID = "headless_observation_v1"
const val HEADLESS_OBSERVATION_SCHEMA_VERSION = 1
const val HEADLESS_OBSERVATION_COMPATIBILITY_POLICY = "v1_additive_only"

val HEADLESS_OBSERVATION_V1_FIELD_ORDER =
    listOf(
        "schema_id",
        "schema_version",
        "compatibility_policy",
        "tick",
        "episode_seed",
        "player",
        "wave",
        "npcs",
    )

data class HeadlessObservationTile(
    val x: Int,
    val y: Int,
    val level: Int,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "x" to x,
            "y" to y,
            "level" to level,
        )

    companion object {
        fun from(tile: Tile): HeadlessObservationTile = HeadlessObservationTile(tile.x, tile.y, tile.level)
    }
}

data class HeadlessObservationProtectionPrayers(
    val protectFromMagic: Boolean,
    val protectFromMissiles: Boolean,
    val protectFromMelee: Boolean,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "protect_from_magic" to protectFromMagic,
            "protect_from_missiles" to protectFromMissiles,
            "protect_from_melee" to protectFromMelee,
        )
}

data class HeadlessObservationLockouts(
    val attackLocked: Boolean,
    val foodLocked: Boolean,
    val drinkLocked: Boolean,
    val comboLocked: Boolean,
    val busyLocked: Boolean,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "attack_locked" to attackLocked,
            "food_locked" to foodLocked,
            "drink_locked" to drinkLocked,
            "combo_locked" to comboLocked,
            "busy_locked" to busyLocked,
        )
}

data class HeadlessObservationConsumables(
    val sharkCount: Int,
    val prayerPotionDoseCount: Int,
    val ammoId: String?,
    val ammoCount: Int,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "shark_count" to sharkCount,
            "prayer_potion_dose_count" to prayerPotionDoseCount,
            "ammo_id" to (ammoId ?: ""),
            "ammo_count" to ammoCount,
        )
}

data class HeadlessObservationPlayer(
    val tile: HeadlessObservationTile,
    val hitpointsCurrent: Int,
    val hitpointsMax: Int,
    val prayerCurrent: Int,
    val prayerMax: Int,
    val runEnergy: Int,
    val runEnergyMax: Int,
    val runEnergyPercent: Int,
    val running: Boolean,
    val protectionPrayers: HeadlessObservationProtectionPrayers,
    val lockouts: HeadlessObservationLockouts,
    val consumables: HeadlessObservationConsumables,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "tile" to tile.toOrderedMap(),
            "hitpoints_current" to hitpointsCurrent,
            "hitpoints_max" to hitpointsMax,
            "prayer_current" to prayerCurrent,
            "prayer_max" to prayerMax,
            "run_energy" to runEnergy,
            "run_energy_max" to runEnergyMax,
            "run_energy_percent" to runEnergyPercent,
            "running" to running,
            "protection_prayers" to protectionPrayers.toOrderedMap(),
            "lockouts" to lockouts.toOrderedMap(),
            "consumables" to consumables.toOrderedMap(),
        )
}

data class HeadlessObservationWave(
    val wave: Int,
    val rotation: Int,
    val remaining: Int,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "wave" to wave,
            "rotation" to rotation,
            "remaining" to remaining,
        )
}

data class HeadlessObservationNpc(
    val visibleIndex: Int,
    val npcIndex: Int,
    val id: String,
    val tile: HeadlessObservationTile,
    val hitpointsCurrent: Int,
    val hitpointsMax: Int,
    val hidden: Boolean,
    val dead: Boolean,
    val underAttack: Boolean,
    val jadTelegraphState: Int,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "visible_index" to visibleIndex,
            "npc_index" to npcIndex,
            "id" to id,
            "tile" to tile.toOrderedMap(),
            "hitpoints_current" to hitpointsCurrent,
            "hitpoints_max" to hitpointsMax,
            "hidden" to hidden,
            "dead" to dead,
            "under_attack" to underAttack,
            "jad_telegraph_state" to jadTelegraphState,
        )
}

data class HeadlessObservationDebugFutureLeakage(
    val enabled: Boolean,
    val fields: List<String>,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "enabled" to enabled,
            "fields" to fields,
        )
}

data class HeadlessObservationV1(
    val schemaId: String = HEADLESS_OBSERVATION_SCHEMA_ID,
    val schemaVersion: Int = HEADLESS_OBSERVATION_SCHEMA_VERSION,
    val compatibilityPolicy: String = HEADLESS_OBSERVATION_COMPATIBILITY_POLICY,
    val tick: Int,
    val episodeSeed: Long,
    val player: HeadlessObservationPlayer,
    val wave: HeadlessObservationWave,
    val npcs: List<HeadlessObservationNpc>,
    val debugFutureLeakage: HeadlessObservationDebugFutureLeakage? = null,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> {
        val map =
            linkedMapOf<String, Any>(
                "schema_id" to schemaId,
                "schema_version" to schemaVersion,
                "compatibility_policy" to compatibilityPolicy,
                "tick" to tick,
                "episode_seed" to episodeSeed,
                "player" to player.toOrderedMap(),
                "wave" to wave.toOrderedMap(),
                "npcs" to npcs.map { it.toOrderedMap() },
            )
        debugFutureLeakage?.let { map["debug_future_leakage"] = it.toOrderedMap() }
        return LinkedHashMap(map)
    }
}

class HeadlessObservationBuilder(
    private val actionAdapter: HeadlessActionAdapter,
) {

    fun build(player: Player, includeFutureLeakage: Boolean = false): HeadlessObservationV1 {
        val activePrayerKey = player.getActivePrayerVarKey()

        val protections =
            HeadlessObservationProtectionPrayers(
                protectFromMagic = player.containsVarbit(activePrayerKey, "protect_from_magic"),
                protectFromMissiles = player.containsVarbit(activePrayerKey, "protect_from_missiles"),
                protectFromMelee = player.containsVarbit(activePrayerKey, "protect_from_melee"),
            )

        val lockouts =
            HeadlessObservationLockouts(
                attackLocked = player.hasClock("action_delay"),
                foodLocked = player.hasClock("food_delay"),
                drinkLocked = player.hasClock("drink_delay"),
                comboLocked = player.hasClock("combo_delay"),
                busyLocked = player.contains("delay") || player.hasClock("stunned"),
            )

        val ammoItem = player.equipment[EquipSlot.Ammo.index]
        val consumables =
            HeadlessObservationConsumables(
                sharkCount = player.inventory.count("shark"),
                prayerPotionDoseCount = fightCavePrayerPotionDoseCount(player),
                ammoId = ammoItem.id.ifBlank { null },
                ammoCount = if (ammoItem.isEmpty()) 0 else ammoItem.amount,
            )

        val observedNpcs =
            actionAdapter
                .visibleNpcTargets(player)
                .mapNotNull { target ->
                    val npc = NPCs.indexed(target.npcIndex) ?: return@mapNotNull null
                    val hitpointsCurrent = npc.levels.get(Skill.Constitution)
                    val hitpointsMax = npc.levels.getMax(Skill.Constitution)
                    HeadlessObservationNpc(
                        visibleIndex = target.visibleIndex,
                        npcIndex = target.npcIndex,
                        id = target.id,
                        tile = HeadlessObservationTile.from(target.tile),
                        hitpointsCurrent = hitpointsCurrent,
                        hitpointsMax = hitpointsMax,
                        hidden = npc.hide,
                        dead = npc.contains("dead") || hitpointsCurrent <= 0,
                        underAttack = npc.hasClock("under_attack"),
                        jadTelegraphState =
                            if (target.id == "tztok_jad") {
                                npc.jadTelegraphState.encoded
                            } else {
                                JadTelegraphState.Idle.encoded
                            },
                    )
                }

        val debugFutureLeakage =
            if (includeFutureLeakage) {
                HeadlessObservationDebugFutureLeakage(
                    enabled = true,
                    fields = listOf("next_jad_attack_style_preview"),
                )
            } else {
                null
            }

        return HeadlessObservationV1(
            tick = GameLoop.tick,
            episodeSeed = player[FIGHT_CAVE_EPISODE_SEED_KEY, -1L],
            player =
                HeadlessObservationPlayer(
                    tile = HeadlessObservationTile.from(player.tile),
                    hitpointsCurrent = player.levels.get(Skill.Constitution),
                    hitpointsMax = player.levels.getMax(Skill.Constitution),
                    prayerCurrent = player.levels.get(Skill.Prayer),
                    prayerMax = player.levels.getMax(Skill.Prayer),
                    runEnergy = player.runEnergy,
                    runEnergyMax = MAX_RUN_ENERGY,
                    runEnergyPercent = (player.runEnergy / MAX_RUN_ENERGY.toDouble() * 100).toInt(),
                    running = player.running,
                    protectionPrayers = protections,
                    lockouts = lockouts,
                    consumables = consumables,
                ),
            wave =
                HeadlessObservationWave(
                    wave = player[FIGHT_CAVE_WAVE_KEY, -1],
                    rotation = player[FIGHT_CAVE_ROTATION_KEY, -1],
                    remaining = player[FIGHT_CAVE_REMAINING_KEY, 0],
                ),
            npcs = observedNpcs,
            debugFutureLeakage = debugFutureLeakage,
        )
    }
}
