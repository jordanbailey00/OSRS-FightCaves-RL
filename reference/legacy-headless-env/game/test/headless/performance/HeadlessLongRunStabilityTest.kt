import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class HeadlessLongRunStabilityTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `headless long run remains stable and bounded`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = false)
        val player = createHeadlessPlayer("headless-long-run")
        runtime.resetFightCaveEpisode(player, seed = 20260305L, startWave = 1)

        val steps = List(4_000) { HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 1) }
        val result = runtime.runFightCaveBatch(player, steps, observeEvery = 250)

        assertEquals(4_000, result.ticksAdvanced)
        assertTrue(result.finalObservation.tick >= 4_000)
        assertTrue(result.finalObservation.player.hitpointsCurrent in 0..result.finalObservation.player.hitpointsMax)
        assertTrue(result.finalObservation.player.prayerCurrent in 0..result.finalObservation.player.prayerMax)
        assertTrue(result.finalObservation.wave.wave in 1..63)
        assertTrue(result.finalObservation.wave.remaining >= 0)

        runtime.shutdown()
    }
}