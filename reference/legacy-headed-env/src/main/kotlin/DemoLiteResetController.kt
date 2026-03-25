data class DemoLiteResetDecision(
    val resetNow: Boolean,
    val autoResetRemainingMillis: Long?,
    val reason: String? = null,
)

class DemoLiteResetController(
    private val autoResetDelayMillis: Long,
) {
    private var manualResetRequested: Boolean = false
    private var armedDeadlineMillis: Long? = null

    fun requestManualReset() {
        manualResetRequested = true
    }

    fun evaluate(
        state: DemoLiteTickState,
        nowMillis: Long = System.currentTimeMillis(),
    ): DemoLiteResetDecision {
        if (manualResetRequested) {
            manualResetRequested = false
            armedDeadlineMillis = null
            return DemoLiteResetDecision(
                resetNow = true,
                autoResetRemainingMillis = null,
                reason = "manual_restart",
            )
        }

        if (!state.terminalCode.isAutoResettable()) {
            armedDeadlineMillis = null
            return DemoLiteResetDecision(resetNow = false, autoResetRemainingMillis = null)
        }

        val deadline =
            armedDeadlineMillis ?: (nowMillis + autoResetDelayMillis).also {
                armedDeadlineMillis = it
            }
        val remaining = (deadline - nowMillis).coerceAtLeast(0L)
        if (remaining == 0L) {
            armedDeadlineMillis = null
            return DemoLiteResetDecision(
                resetNow = true,
                autoResetRemainingMillis = 0L,
                reason = state.terminalCode.name.lowercase(),
            )
        }
        return DemoLiteResetDecision(
            resetNow = false,
            autoResetRemainingMillis = remaining,
            reason = state.terminalCode.name.lowercase(),
        )
    }

    private fun DemoLiteTerminalCode.isAutoResettable(): Boolean =
        this == DemoLiteTerminalCode.PLAYER_DEATH ||
            this == DemoLiteTerminalCode.CAVE_COMPLETE ||
            this == DemoLiteTerminalCode.INVALID_STATE
}
