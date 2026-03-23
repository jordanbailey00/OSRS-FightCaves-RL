import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.entity.character.player.skill.Skill
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ObservationSchemaCompletenessTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `observation contains required player wave and consumable fields`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-observation-schema")

        runtime.resetFightCaveEpisode(player, seed = 101L, startWave = 1)
        val observation = runtime.observeFightCave(player)

        assertEquals(HEADLESS_OBSERVATION_SCHEMA_ID, observation.schemaId)
        assertEquals(HEADLESS_OBSERVATION_SCHEMA_VERSION, observation.schemaVersion)
        assertEquals(HEADLESS_OBSERVATION_COMPATIBILITY_POLICY, observation.compatibilityPolicy)
        assertEquals(HEADLESS_OBSERVATION_V1_FIELD_ORDER, observation.toOrderedMap().keys.toList())

        assertEquals(player.tile.x, observation.player.tile.x)
        assertEquals(player.tile.y, observation.player.tile.y)
        assertEquals(player.tile.level, observation.player.tile.level)
        assertEquals(player.levels.getMax(Skill.Constitution), observation.player.hitpointsMax)
        assertEquals(player.levels.getMax(Skill.Prayer), observation.player.prayerMax)

        assertEquals(20, observation.player.consumables.sharkCount)
        assertEquals(32, observation.player.consumables.prayerPotionDoseCount)
        assertEquals("adamant_bolts", observation.player.consumables.ammoId)
        assertEquals(1000, observation.player.consumables.ammoCount)

        assertTrue(observation.wave.wave >= 1)
        assertTrue(observation.wave.remaining >= 0)
    }
}
