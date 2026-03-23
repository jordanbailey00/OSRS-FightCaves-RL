import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class ParityHarnessSingleWaveTraceTest {

    @Test
    fun `single wave trace matches oracle and headless tick-by-tick`() {
        val harness = ParityHarness()
        val result =
            harness.runAndCompare(
                seed = 11_001L,
                actionTrace = paritySingleWaveTrace(),
                startWave = 1,
                playerName = "parity-single-wave",
                settingsOverrides = parityTestOverrides(),
                configurePlayer = ::parityPlayerSetup,
            )

        result.requirePassed()
        assertNull(result.mismatch)
        assertEquals(paritySingleWaveTrace().size + 1, result.oracle.snapshots.size)
        assertEquals(result.oracle.snapshots.size, result.headless.snapshots.size)
    }
}
