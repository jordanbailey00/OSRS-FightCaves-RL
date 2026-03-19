package headless.fast

data class FastStepMetrics(
    val applyActionsNanos: Long,
    val tickNanos: Long,
    val observeFlatNanos: Long,
    val projectionNanos: Long,
    val totalNanos: Long,
) {
    fun envStepsPerSecond(envCount: Int): Double =
        if (envCount <= 0 || totalNanos <= 0L) {
            0.0
        } else {
            envCount * 1_000_000_000.0 / totalNanos.toDouble()
        }
}
