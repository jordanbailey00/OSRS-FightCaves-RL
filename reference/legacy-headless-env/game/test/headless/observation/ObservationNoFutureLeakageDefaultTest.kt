import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ObservationNoFutureLeakageDefaultTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `observation omits future leakage fields by default`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-observation-no-leak")
        runtime.resetFightCaveEpisode(player, seed = 202L, startWave = 1)

        val defaultObservation = runtime.observeFightCave(player)
        assertNull(defaultObservation.debugFutureLeakage)
        assertFalse(defaultObservation.toOrderedMap().containsKey("debug_future_leakage"))

        val debugObservation = runtime.observeFightCave(player, includeFutureLeakage = true)
        assertNotNull(debugObservation.debugFutureLeakage)
        assertTrue(debugObservation.toOrderedMap().containsKey("debug_future_leakage"))
    }
}
