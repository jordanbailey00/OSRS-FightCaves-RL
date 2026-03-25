package headless.fast

data class FastResetBatchResult(
    val envCount: Int,
    val slotIndices: IntArray,
    val flatObservationFeatureCount: Int,
    val rewardFeatureCount: Int,
    val flatObservations: FloatArray,
    val rewardFeatures: FloatArray,
    val terminalCodes: IntArray,
    val terminated: BooleanArray,
    val truncated: BooleanArray,
    val episodeTicks: IntArray,
    val episodeSteps: IntArray,
    val parityTraces: List<FastParityTrace>,
    val elapsedNanos: Long,
) {
    val envStepsPerSecond: Double
        get() =
            if (elapsedNanos <= 0L || envCount <= 0) {
                0.0
            } else {
                envCount * 1_000_000_000.0 / elapsedNanos.toDouble()
            }
}

data class FastStepBatchResult(
    val envCount: Int,
    val slotIndices: IntArray,
    val actionIds: IntArray,
    val actionAccepted: BooleanArray,
    val rejectionCodes: IntArray,
    val flatObservationFeatureCount: Int,
    val rewardFeatureCount: Int,
    val flatObservations: FloatArray,
    val rewardFeatures: FloatArray,
    val terminalCodes: IntArray,
    val terminated: BooleanArray,
    val truncated: BooleanArray,
    val episodeTicks: IntArray,
    val episodeSteps: IntArray,
    val parityTraces: List<FastParityTrace>,
    val metrics: FastStepMetrics,
) {
    val envStepsPerSecond: Double
        get() = metrics.envStepsPerSecond(envCount)
}
