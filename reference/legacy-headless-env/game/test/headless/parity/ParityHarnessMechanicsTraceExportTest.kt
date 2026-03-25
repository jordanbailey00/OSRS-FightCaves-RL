import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ParityHarnessMechanicsTraceExportTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `oracle mechanics parity trace export emits shared fast trace shape`() {
        val harness = ParityHarness()
        val traces =
            harness.collectMechanicsParityTrace(
                pathId = ParityRuntimePath.Oracle.id,
                seed = 11_001L,
                packedActions =
                    intArrayOf(
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                    ),
                startWave = 1,
                tickCap = 32,
                playerName = "parity-mechanics-trace",
                settingsOverrides = parityTestOverrides(),
            )

        assertEquals(3, traces.size)
        assertEquals("reset", traces.first().actionName)
        assertEquals("wait", traces[1].actionName)
        assertTrue(traces.all { it.tickIndex >= 0 })
        assertEquals(0, traces.first().terminalCode)
    }
}
