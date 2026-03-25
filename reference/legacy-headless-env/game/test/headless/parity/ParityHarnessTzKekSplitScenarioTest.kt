import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class ParityHarnessTzKekSplitScenarioTest {

    @Test
    fun `tz-kek split scenario matches oracle and headless`() {
        val harness = ParityHarness()
        val result =
            harness.runAndCompare(
                seed = 44_004L,
                actionTrace = parityTzKekSplitTrace(),
                startWave = 3,
                playerName = "parity-tzkek-split",
                settingsOverrides = parityTestOverrides(),
                configurePlayer = ::parityPlayerSetup,
                stepHook = tzKekSplitHook,
            )

        result.requirePassed()
        assertNull(result.mismatch)

        val oraclePeakSplits =
            result.oracle.snapshots.maxOf { snapshot ->
                snapshot.fightCaveNpcs.count { it.id == "tz_kek_spawn" }
            }
        val headlessPeakSplits =
            result.headless.snapshots.maxOf { snapshot ->
                snapshot.fightCaveNpcs.count { it.id == "tz_kek_spawn" }
            }

        assertEquals(oraclePeakSplits, headlessPeakSplits)
    }
}
