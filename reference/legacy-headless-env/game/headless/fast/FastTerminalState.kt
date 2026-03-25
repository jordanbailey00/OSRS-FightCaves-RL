package headless.fast

const val FAST_TERMINAL_NONE = 0
const val FAST_TERMINAL_PLAYER_DEATH = 1
const val FAST_TERMINAL_CAVE_COMPLETE = 2
const val FAST_TERMINAL_TICK_CAP = 3
const val FAST_TERMINAL_INVALID_STATE = 4
const val FAST_TERMINAL_ORACLE_ABORT = 5

data class FastTerminalState(
    val terminated: Boolean,
    val truncated: Boolean,
    val terminalCode: Int,
    val terminalReason: String? = null,
) {
    companion object {
        val NONE =
            FastTerminalState(
                terminated = false,
                truncated = false,
                terminalCode = FAST_TERMINAL_NONE,
                terminalReason = null,
            )
    }
}

object FastTerminalStateEvaluator {

    fun infer(
        playerHitpointsCurrent: Int,
        waveId: Int,
        remainingNpcs: Int,
        observationTick: Int,
        episodeStartTick: Int?,
        tickCap: Int,
    ): FastTerminalState {
        if (playerHitpointsCurrent <= 0) {
            return FastTerminalState(
                terminated = true,
                truncated = false,
                terminalCode = FAST_TERMINAL_PLAYER_DEATH,
                terminalReason = "player_death",
            )
        }
        if (waveId == 63 && remainingNpcs == 0) {
            return FastTerminalState(
                terminated = true,
                truncated = false,
                terminalCode = FAST_TERMINAL_CAVE_COMPLETE,
                terminalReason = "cave_complete",
            )
        }
        if (episodeStartTick != null && observationTick - episodeStartTick >= tickCap) {
            return FastTerminalState(
                terminated = false,
                truncated = true,
                terminalCode = FAST_TERMINAL_TICK_CAP,
                terminalReason = "tick_cap",
            )
        }
        return FastTerminalState.NONE
    }
}
