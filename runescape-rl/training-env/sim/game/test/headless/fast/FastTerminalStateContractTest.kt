import headless.fast.FAST_TERMINAL_CAVE_COMPLETE
import headless.fast.FAST_TERMINAL_PLAYER_DEATH
import headless.fast.FAST_TERMINAL_TICK_CAP
import headless.fast.FastTerminalStateEvaluator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FastTerminalStateContractTest {

    @Test
    fun `terminal evaluator emits player death before all other terminal states`() {
        val terminal =
            FastTerminalStateEvaluator.infer(
                playerHitpointsCurrent = 0,
                waveId = 63,
                remainingNpcs = 0,
                observationTick = 10,
                episodeStartTick = 0,
                tickCap = 1,
            )

        assertTrue(terminal.terminated)
        assertFalse(terminal.truncated)
        assertEquals(FAST_TERMINAL_PLAYER_DEATH, terminal.terminalCode)
    }

    @Test
    fun `terminal evaluator emits cave complete when jad wave is cleared`() {
        val terminal =
            FastTerminalStateEvaluator.infer(
                playerHitpointsCurrent = 10,
                waveId = 63,
                remainingNpcs = 0,
                observationTick = 10,
                episodeStartTick = 0,
                tickCap = 100,
            )

        assertTrue(terminal.terminated)
        assertEquals(FAST_TERMINAL_CAVE_COMPLETE, terminal.terminalCode)
    }

    @Test
    fun `terminal evaluator emits tick cap truncation when elapsed ticks reach the cap`() {
        val terminal =
            FastTerminalStateEvaluator.infer(
                playerHitpointsCurrent = 10,
                waveId = 1,
                remainingNpcs = 1,
                observationTick = 5,
                episodeStartTick = 0,
                tickCap = 5,
            )

        assertFalse(terminal.terminated)
        assertTrue(terminal.truncated)
        assertEquals(FAST_TERMINAL_TICK_CAP, terminal.terminalCode)
    }
}
