import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ParityHarnessJadHealerScenarioTest {

    @Test
    fun `jad healer trigger scenario matches oracle and headless`() {
        val harness = ParityHarness()
        val result =
            harness.runAndCompare(
                seed = 33_003L,
                actionTrace = parityJadHealerTrace(),
                startWave = 63,
                playerName = "parity-jad-healers",
                settingsOverrides = parityTestOverrides(),
                configurePlayer = ::parityPlayerSetup,
                stepHook = jadHalfHpHook,
            )

        val oracleHasJad = result.oracle.snapshots.any { snapshot -> snapshot.fightCaveNpcs.any { it.id == "tztok_jad" } }
        val headlessHasJad = result.headless.snapshots.any { snapshot -> snapshot.fightCaveNpcs.any { it.id == "tztok_jad" } }
        assertTrue(oracleHasJad)
        assertTrue(headlessHasJad)

        val oracleHealerCount = result.oracle.snapshots.last().fightCaveNpcs.count { it.id == "yt_hur_kot" }
        val headlessHealerCount = result.headless.snapshots.last().fightCaveNpcs.count { it.id == "yt_hur_kot" }
        assertEquals(oracleHealerCount, headlessHealerCount)

        val oracleFinal = result.oracle.snapshots.last().observation.wave
        val headlessFinal = result.headless.snapshots.last().observation.wave
        assertEquals(oracleFinal.wave, headlessFinal.wave)
        assertEquals(oracleFinal.remaining, headlessFinal.remaining)
    }
}
