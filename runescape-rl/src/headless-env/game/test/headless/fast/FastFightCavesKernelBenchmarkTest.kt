import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt
import kotlin.test.assertTrue

internal class FastFightCavesKernelBenchmarkTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `fast kernel env-only benchmark harness runs and reports positive throughput`() {
        val result = FastFightCavesKernelBenchmark.runWaitBenchmark(envCount = 16, steps = 32)
        println("fast_kernel_env_sps=${result.envStepsPerSecond}")
        assertTrue(
            result.envStepsPerSecond > 100.0,
            "Expected fast kernel env-only throughput > 100 env/s, got ${result.envStepsPerSecond.roundToInt()} env/s.",
        )
    }
}
