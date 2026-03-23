import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ParityHarnessFullRunTraceTest {

    @Test
    fun `full run trace matches oracle and headless`() {
        val trace = parityFullRunTrace(steps = 160)
        val harness = ParityHarness()
        val result =
            harness.runAndCompare(
                seed = 22_002L,
                actionTrace = trace,
                startWave = 1,
                playerName = "parity-full-run",
                settingsOverrides = parityTestOverrides(),
                configurePlayer = ::parityPlayerSetup,
                stepHook = fullRunWaveClearHook,
            )

        assertEquals(result.oracle.snapshots.size, result.headless.snapshots.size)

        val oracleVisitedWaves = result.oracle.snapshots.map { it.observation.wave.wave }.filter { it > 0 }.toSet()
        val headlessVisitedWaves = result.headless.snapshots.map { it.observation.wave.wave }.filter { it > 0 }.toSet()
        assertTrue(oracleVisitedWaves.contains(1) && oracleVisitedWaves.contains(63))
        assertTrue(headlessVisitedWaves.contains(1) && headlessVisitedWaves.contains(63))

        val oracleFinal = result.oracle.snapshots.last().observation.wave
        val headlessFinal = result.headless.snapshots.last().observation.wave
        assertTrue(oracleFinal.wave <= 0)
        assertTrue(headlessFinal.wave <= 0)
    }
}
