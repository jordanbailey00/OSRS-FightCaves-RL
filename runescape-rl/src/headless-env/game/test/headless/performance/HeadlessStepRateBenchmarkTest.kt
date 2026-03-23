import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt
import kotlin.test.assertTrue

internal class HeadlessStepRateBenchmarkTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `headless step rate benchmark meets minimum throughput`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = false)
        val player = createHeadlessPlayer("headless-benchmark")
        runtime.resetFightCaveEpisode(player, seed = 20260305L, startWave = 1)

        val steps = List(2_000) { HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 1) }
        val result = runtime.runFightCaveBatch(player, steps, observeEvery = 0)

        val ticksPerSecond = result.ticksPerSecond
        assertTrue(result.ticksAdvanced == 2_000)
        assertTrue(ticksPerSecond > 100.0, "Expected headless throughput > 100 ticks/s, got ${ticksPerSecond.roundToInt()} ticks/s.")

        runtime.shutdown()
    }
}