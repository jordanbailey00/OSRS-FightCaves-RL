import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

internal class RngCounterMonotonicityTest {

    @Test
    fun `rng call counter is monotonic during deterministic replay`() {
        val result = executeHeadlessReplay(seed = 5150L, trace = deterministicFightCaveTrace(), playerName = "rng-counter-monotonic")

        val counters = result.snapshots.map { it.rngCallCount }
        assertTrue(counters.isNotEmpty())

        for (index in 1 until counters.size) {
            assertTrue(counters[index] >= counters[index - 1], "RNG call count regressed at snapshot $index")
        }

        assertTrue(result.rngDiagnostics.trackingEnabled)
        assertTrue(result.rngDiagnostics.callCount >= counters.last())
        assertTrue(result.rngDiagnostics.callCount > 0)
    }
}
