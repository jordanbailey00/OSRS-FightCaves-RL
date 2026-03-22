import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ObservationVersioningContractTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `observation versioning contract is stable and explicit`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-observation-version")
        runtime.resetFightCaveEpisode(player, seed = 303L, startWave = 1)

        val observation = runtime.observeFightCave(player)

        assertEquals("headless_observation_v1", HEADLESS_OBSERVATION_SCHEMA_ID)
        assertEquals(1, HEADLESS_OBSERVATION_SCHEMA_VERSION)
        assertEquals("v1_additive_only", HEADLESS_OBSERVATION_COMPATIBILITY_POLICY)

        assertEquals(HEADLESS_OBSERVATION_SCHEMA_ID, observation.schemaId)
        assertEquals(HEADLESS_OBSERVATION_SCHEMA_VERSION, observation.schemaVersion)
        assertEquals(HEADLESS_OBSERVATION_COMPATIBILITY_POLICY, observation.compatibilityPolicy)
        assertEquals(HEADLESS_OBSERVATION_V1_FIELD_ORDER, observation.toOrderedMap().keys.toList())
    }
}
