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

const val HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA_ID = "headless_training_flat_observation_v1"
const val HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA_VERSION = 1
const val HEADLESS_TRAINING_FLAT_OBSERVATION_DTYPE = "float32"
const val HEADLESS_TRAINING_FLAT_OBSERVATION_BASE_FIELD_COUNT = 30
const val HEADLESS_TRAINING_FLAT_OBSERVATION_NPC_FIELD_COUNT = 13
const val HEADLESS_TRAINING_FLAT_OBSERVATION_MAX_VISIBLE_NPCS = 8
const val HEADLESS_TRAINING_FLAT_OBSERVATION_FEATURE_COUNT =
    HEADLESS_TRAINING_FLAT_OBSERVATION_BASE_FIELD_COUNT +
        (HEADLESS_TRAINING_FLAT_OBSERVATION_NPC_FIELD_COUNT * HEADLESS_TRAINING_FLAT_OBSERVATION_MAX_VISIBLE_NPCS)

private val HEADLESS_TRAINING_FLAT_AMMO_ID_CODES =
    mapOf(
        "" to 0,
        "adamant_bolts" to 1,
    )

private val HEADLESS_TRAINING_FLAT_NPC_ID_CODES =
    mapOf(
        "tz_kih" to 0,
        "tz_kih_spawn_point" to 1,
        "tz_kek" to 2,
        "tz_kek_spawn_point" to 3,
        "tz_kek_spawn" to 4,
        "tok_xil" to 5,
        "tok_xil_spawn_point" to 6,
        "yt_mej_kot" to 7,
        "yt_mej_kot_spawn_point" to 8,
        "ket_zek" to 9,
        "ket_zek_spawn_point" to 10,
        "tztok_jad" to 11,
        "yt_hur_kot" to 12,
    )

data class HeadlessTrainingFlatObservationV1(
    val schemaId: String = HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA_ID,
    val schemaVersion: Int = HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA_VERSION,
    val dtype: String = HEADLESS_TRAINING_FLAT_OBSERVATION_DTYPE,
    val featureCount: Int = HEADLESS_TRAINING_FLAT_OBSERVATION_FEATURE_COUNT,
    val maxVisibleNpcs: Int = HEADLESS_TRAINING_FLAT_OBSERVATION_MAX_VISIBLE_NPCS,
    val values: FloatArray,
)

data class HeadlessTrainingFlatObservationBatchV1(
    val schemaId: String = HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA_ID,
    val schemaVersion: Int = HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA_VERSION,
    val dtype: String = HEADLESS_TRAINING_FLAT_OBSERVATION_DTYPE,
    val featureCount: Int = HEADLESS_TRAINING_FLAT_OBSERVATION_FEATURE_COUNT,
    val maxVisibleNpcs: Int = HEADLESS_TRAINING_FLAT_OBSERVATION_MAX_VISIBLE_NPCS,
    val envCount: Int,
    val values: FloatArray,
)

fun packFlatObservationBatch(
    observations: List<HeadlessTrainingFlatObservationV1>,
): HeadlessTrainingFlatObservationBatchV1 {
    if (observations.isEmpty()) {
        return HeadlessTrainingFlatObservationBatchV1(
            envCount = 0,
            values = FloatArray(0),
        )
    }
    val featureCount = observations.first().featureCount
    val packed = FloatArray(observations.size * featureCount)
    observations.forEachIndexed { envIndex, observation ->
        require(observation.featureCount == featureCount) {
            "Flat observation batch feature-count drift: expected $featureCount, got ${observation.featureCount}."
        }
        observation.values.copyInto(
            destination = packed,
            destinationOffset = envIndex * featureCount,
        )
    }
    return HeadlessTrainingFlatObservationBatchV1(
        envCount = observations.size,
        values = packed,
    )
}

class HeadlessFlatObservationBuilder(
    private val actionAdapter: HeadlessActionAdapter,
) {

    fun build(player: Player): HeadlessTrainingFlatObservationV1 {
        val activePrayerKey = player.getActivePrayerVarKey()
        val values = FloatArray(HEADLESS_TRAINING_FLAT_OBSERVATION_FEATURE_COUNT)

        values[0] = HEADLESS_OBSERVATION_SCHEMA_VERSION.toFloat()
        values[1] = GameLoop.tick.toFloat()
        values[2] = player[FIGHT_CAVE_EPISODE_SEED_KEY, -1L].toFloat()
        values[3] = player.tile.x.toFloat()
        values[4] = player.tile.y.toFloat()
        values[5] = player.tile.level.toFloat()
        values[6] = player.levels.get(Skill.Constitution).toFloat()
        values[7] = player.levels.getMax(Skill.Constitution).toFloat()
        values[8] = player.levels.get(Skill.Prayer).toFloat()
        values[9] = player.levels.getMax(Skill.Prayer).toFloat()
        values[10] = player.runEnergy.toFloat()
        values[11] = MAX_RUN_ENERGY.toFloat()
        values[12] = (player.runEnergy / MAX_RUN_ENERGY.toDouble() * 100).toFloat()
        values[13] = if (player.running) 1f else 0f
        values[14] = if (player.containsVarbit(activePrayerKey, "protect_from_magic")) 1f else 0f
        values[15] = if (player.containsVarbit(activePrayerKey, "protect_from_missiles")) 1f else 0f
        values[16] = if (player.containsVarbit(activePrayerKey, "protect_from_melee")) 1f else 0f
        values[17] = if (player.hasClock("action_delay")) 1f else 0f
        values[18] = if (player.hasClock("food_delay")) 1f else 0f
        values[19] = if (player.hasClock("drink_delay")) 1f else 0f
        values[20] = if (player.hasClock("combo_delay")) 1f else 0f
        values[21] = if (player.contains("delay") || player.hasClock("stunned")) 1f else 0f
        values[22] = player.inventory.count("shark").toFloat()
        values[23] = fightCavePrayerPotionDoseCount(player).toFloat()
        val ammoItem = player.equipment[EquipSlot.Ammo.index]
        values[24] = ammoIdCode(ammoItem.id).toFloat()
        values[25] = if (ammoItem.isEmpty()) 0f else ammoItem.amount.toFloat()
        values[26] = player[FIGHT_CAVE_WAVE_KEY, -1].toFloat()
        values[27] = player[FIGHT_CAVE_ROTATION_KEY, -1].toFloat()
        values[28] = player[FIGHT_CAVE_REMAINING_KEY, 0].toFloat()

        val observedNpcs =
            actionAdapter
                .visibleNpcTargets(player)
                .mapNotNull { target ->
                    val npc = NPCs.indexed(target.npcIndex) ?: return@mapNotNull null
                    FlatObservedNpc(
                        visibleIndex = target.visibleIndex,
                        npcIndex = target.npcIndex,
                        idCode = npcIdCode(target.id),
                        tileX = target.tile.x,
                        tileY = target.tile.y,
                        tileLevel = target.tile.level,
                        hitpointsCurrent = npc.levels.get(Skill.Constitution),
                        hitpointsMax = npc.levels.getMax(Skill.Constitution),
                        hidden = npc.hide,
                        dead = npc.contains("dead") || npc.levels.get(Skill.Constitution) <= 0,
                        underAttack = npc.hasClock("under_attack"),
                        jadTelegraphState =
                            if (target.id == "tztok_jad") {
                                npc.jadTelegraphState.encoded
                            } else {
                                JadTelegraphState.Idle.encoded
                            },
                    )
                }

        values[29] = observedNpcs.size.toFloat()
        for (slotIndex in 0 until HEADLESS_TRAINING_FLAT_OBSERVATION_MAX_VISIBLE_NPCS) {
            if (slotIndex >= observedNpcs.size) {
                continue
            }
            val npc = observedNpcs[slotIndex]
            val offset = HEADLESS_TRAINING_FLAT_OBSERVATION_BASE_FIELD_COUNT +
                (slotIndex * HEADLESS_TRAINING_FLAT_OBSERVATION_NPC_FIELD_COUNT)
            values[offset] = 1f
            values[offset + 1] = npc.visibleIndex.toFloat()
            values[offset + 2] = npc.npcIndex.toFloat()
            values[offset + 3] = npc.idCode.toFloat()
            values[offset + 4] = npc.tileX.toFloat()
            values[offset + 5] = npc.tileY.toFloat()
            values[offset + 6] = npc.tileLevel.toFloat()
            values[offset + 7] = npc.hitpointsCurrent.toFloat()
            values[offset + 8] = npc.hitpointsMax.toFloat()
            values[offset + 9] = if (npc.hidden) 1f else 0f
            values[offset + 10] = if (npc.dead) 1f else 0f
            values[offset + 11] = if (npc.underAttack) 1f else 0f
            values[offset + 12] = npc.jadTelegraphState.toFloat()
        }

        return HeadlessTrainingFlatObservationV1(values = values)
    }

    private fun ammoIdCode(ammoId: String): Int =
        HEADLESS_TRAINING_FLAT_AMMO_ID_CODES[ammoId]
            ?: error("Unsupported flat observation ammo id '$ammoId'.")

    private fun npcIdCode(npcId: String): Int =
        HEADLESS_TRAINING_FLAT_NPC_ID_CODES[npcId]
            ?: error("Unsupported flat observation npc id '$npcId'.")

}

private data class FlatObservedNpc(
    val visibleIndex: Int,
    val npcIndex: Int,
    val idCode: Int,
    val tileX: Int,
    val tileY: Int,
    val tileLevel: Int,
    val hitpointsCurrent: Int,
    val hitpointsMax: Int,
    val hidden: Boolean,
    val dead: Boolean,
    val underAttack: Boolean,
    val jadTelegraphState: Int,
)
