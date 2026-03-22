import content.area.karamja.tzhaar_city.JadTelegraphState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

internal class FlatObservationProjectionEqualityTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `flat observation schema contract is stable and explicit`() {
        assertEquals("headless_training_flat_observation_v1", HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA_ID)
        assertEquals(1, HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA_VERSION)
        assertEquals("float32", HEADLESS_TRAINING_FLAT_OBSERVATION_DTYPE)
        assertEquals(30, HEADLESS_TRAINING_FLAT_OBSERVATION_BASE_FIELD_COUNT)
        assertEquals(13, HEADLESS_TRAINING_FLAT_OBSERVATION_NPC_FIELD_COUNT)
        assertEquals(8, HEADLESS_TRAINING_FLAT_OBSERVATION_MAX_VISIBLE_NPCS)
        assertEquals(134, HEADLESS_TRAINING_FLAT_OBSERVATION_FEATURE_COUNT)
    }

    @Test
    fun `flat observation matches raw projection for representative state`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("flat-observation-projection")
        runtime.resetFightCaveEpisode(player, seed = 4_201L, startWave = 1)

        val raw = runtime.observeFightCave(player)
        val flat = runtime.observeFightCaveFlat(player)

        assertEquals(HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA_ID, flat.schemaId)
        assertEquals(HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA_VERSION, flat.schemaVersion)
        assertEquals(HEADLESS_TRAINING_FLAT_OBSERVATION_DTYPE, flat.dtype)
        assertEquals(HEADLESS_TRAINING_FLAT_OBSERVATION_FEATURE_COUNT, flat.featureCount)
        assertEquals(HEADLESS_TRAINING_FLAT_OBSERVATION_MAX_VISIBLE_NPCS, flat.maxVisibleNpcs)
        assertContentEquals(projectRawObservationToFlat(raw), flat.values)
    }

    @Test
    fun `flat observation preserves jad telegraph projection exactly`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("flat-observation-jad-telegraph")
        runtime.resetFightCaveEpisode(player, seed = 9_901L, startWave = 63)
        spawnJadWithTelegraphedAttack(player, "magic")

        val raw = runtime.observeFightCave(player)
        val flat = runtime.observeFightCaveFlat(player)

        assertContentEquals(projectRawObservationToFlat(raw), flat.values)
        val jadSlots = raw.npcs.filter { it.id == "tztok_jad" }
        assertEquals(1, jadSlots.size)
        assertEquals(JadTelegraphState.MagicWindup.encoded, jadSlots.single().jadTelegraphState)
    }
}

private fun projectRawObservationToFlat(observation: HeadlessObservationV1): FloatArray {
    val values = FloatArray(HEADLESS_TRAINING_FLAT_OBSERVATION_FEATURE_COUNT)
    values[0] = observation.schemaVersion.toFloat()
    values[1] = observation.tick.toFloat()
    values[2] = observation.episodeSeed.toFloat()
    values[3] = observation.player.tile.x.toFloat()
    values[4] = observation.player.tile.y.toFloat()
    values[5] = observation.player.tile.level.toFloat()
    values[6] = observation.player.hitpointsCurrent.toFloat()
    values[7] = observation.player.hitpointsMax.toFloat()
    values[8] = observation.player.prayerCurrent.toFloat()
    values[9] = observation.player.prayerMax.toFloat()
    values[10] = observation.player.runEnergy.toFloat()
    values[11] = observation.player.runEnergyMax.toFloat()
    values[12] = observation.player.runEnergyPercent.toFloat()
    values[13] = if (observation.player.running) 1f else 0f
    values[14] = if (observation.player.protectionPrayers.protectFromMagic) 1f else 0f
    values[15] = if (observation.player.protectionPrayers.protectFromMissiles) 1f else 0f
    values[16] = if (observation.player.protectionPrayers.protectFromMelee) 1f else 0f
    values[17] = if (observation.player.lockouts.attackLocked) 1f else 0f
    values[18] = if (observation.player.lockouts.foodLocked) 1f else 0f
    values[19] = if (observation.player.lockouts.drinkLocked) 1f else 0f
    values[20] = if (observation.player.lockouts.comboLocked) 1f else 0f
    values[21] = if (observation.player.lockouts.busyLocked) 1f else 0f
    values[22] = observation.player.consumables.sharkCount.toFloat()
    values[23] = observation.player.consumables.prayerPotionDoseCount.toFloat()
    values[24] = ammoCode(observation.player.consumables.ammoId.orEmpty()).toFloat()
    values[25] = observation.player.consumables.ammoCount.toFloat()
    values[26] = observation.wave.wave.toFloat()
    values[27] = observation.wave.rotation.toFloat()
    values[28] = observation.wave.remaining.toFloat()
    values[29] = observation.npcs.size.toFloat()

    for (slotIndex in 0 until HEADLESS_TRAINING_FLAT_OBSERVATION_MAX_VISIBLE_NPCS) {
        if (slotIndex >= observation.npcs.size) {
            continue
        }
        val npc = observation.npcs[slotIndex]
        val offset = HEADLESS_TRAINING_FLAT_OBSERVATION_BASE_FIELD_COUNT +
            (slotIndex * HEADLESS_TRAINING_FLAT_OBSERVATION_NPC_FIELD_COUNT)
        values[offset] = 1f
        values[offset + 1] = npc.visibleIndex.toFloat()
        values[offset + 2] = npc.npcIndex.toFloat()
        values[offset + 3] = npcIdCode(npc.id).toFloat()
        values[offset + 4] = npc.tile.x.toFloat()
        values[offset + 5] = npc.tile.y.toFloat()
        values[offset + 6] = npc.tile.level.toFloat()
        values[offset + 7] = npc.hitpointsCurrent.toFloat()
        values[offset + 8] = npc.hitpointsMax.toFloat()
        values[offset + 9] = if (npc.hidden) 1f else 0f
        values[offset + 10] = if (npc.dead) 1f else 0f
        values[offset + 11] = if (npc.underAttack) 1f else 0f
        values[offset + 12] = npc.jadTelegraphState.toFloat()
    }
    return values
}

private fun ammoCode(ammoId: String): Int =
    when (ammoId) {
        "" -> 0
        "adamant_bolts" -> 1
        else -> error("Unsupported ammo id in projection test: '$ammoId'.")
    }

private fun npcIdCode(npcId: String): Int =
    when (npcId) {
        "tz_kih" -> 0
        "tz_kih_spawn_point" -> 1
        "tz_kek" -> 2
        "tz_kek_spawn_point" -> 3
        "tz_kek_spawn" -> 4
        "tok_xil" -> 5
        "tok_xil_spawn_point" -> 6
        "yt_mej_kot" -> 7
        "yt_mej_kot_spawn_point" -> 8
        "ket_zek" -> 9
        "ket_zek_spawn_point" -> 10
        "tztok_jad" -> 11
        "yt_hur_kot" -> 12
        else -> error("Unsupported npc id in projection test: '$npcId'.")
    }
