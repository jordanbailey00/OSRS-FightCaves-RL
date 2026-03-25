import headless.fast.FastActionCodec
import headless.fast.FastEpisodeConfig
import headlessTickStageOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Multi-env SPS sweep with per-stage profiling breakdown.
 *
 * Runs [1, 4, 16, 64] envs x 3 replicates with warmup.
 * Captures: env SPS, per-phase nanos (apply/tick/observe/projection),
 *           per-tick-stage nanos (PlayerResetTask, NPCResetTask, etc.),
 *           and reset timing.
 */
internal class FastKernelProfilingSweepTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `profiling sweep across env counts`() {
        val envCounts = listOf(1, 4, 16, 64)
        val warmupRounds = 64
        val measurementRounds = 128
        val replicateCount = 3

        val results = mutableMapOf<Int, SweepResult>()

        for (envCount in envCounts) {
            resetHeadlessTestRuntime()
            val replicates = runReplicates(envCount, warmupRounds, measurementRounds, replicateCount)
            results[envCount] = summarize(envCount, replicates)
            resetHeadlessTestRuntime()
        }

        println("\n====== PROFILING SWEEP RESULTS ======\n")

        // SPS table
        println("--- Env SPS (median / min / max) ---")
        println(String.format("%-8s  %-12s  %-12s  %-12s", "Envs", "Median", "Min", "Max"))
        for ((envCount, result) in results) {
            val spsValues = result.replicates.map { it.envSps }
            println(
                String.format(
                    "%-8d  %-12.0f  %-12.0f  %-12.0f",
                    envCount, median(spsValues), spsValues.min(), spsValues.max(),
                ),
            )
        }

        // Phase breakdown for 16 envs (most representative)
        val representative = results[16] ?: results.values.first()
        println("\n--- Phase Breakdown (envs=${representative.envCount}, median of $replicateCount replicates, $measurementRounds rounds) ---")
        val phases = listOf("apply_actions", "tick", "observe_flat", "projection")
        val phaseNanos = listOf(
            representative.medianApplyNanos,
            representative.medianTickNanos,
            representative.medianObserveNanos,
            representative.medianProjectionNanos,
        )
        val totalPhaseNanos = phaseNanos.sum()
        println(String.format("%-20s  %12s  %8s", "Phase", "Nanos", "Share"))
        for (i in phases.indices) {
            val pct = if (totalPhaseNanos > 0) phaseNanos[i] * 100.0 / totalPhaseNanos else 0.0
            println(String.format("%-20s  %12d  %7.1f%%", phases[i], phaseNanos[i], pct))
        }
        println(String.format("%-20s  %12d  %7.1f%%", "TOTAL", totalPhaseNanos, 100.0))

        // Per-tick-stage breakdown
        println("\n--- Per-Tick-Stage Breakdown (envs=${representative.envCount}, last replicate last tick) ---")
        val stageNames = headlessTickStageOrder
        val stageNanos = representative.lastTickStageNanos
        val totalStageNanos = stageNanos.sum()
        println(String.format("%-25s  %12s  %8s", "Stage", "Nanos", "Share"))
        for (i in stageNames.indices) {
            val ns = if (i < stageNanos.size) stageNanos[i] else 0L
            val pct = if (totalStageNanos > 0) ns * 100.0 / totalStageNanos else 0.0
            println(String.format("%-25s  %12d  %7.1f%%", stageNames[i], ns, pct))
        }
        println(String.format("%-25s  %12d  %7.1f%%", "TOTAL", totalStageNanos, 100.0))

        // Reset timing
        println("\n--- Reset Timing (median nanos) ---")
        for ((envCount, result) in results) {
            val resetValues = result.replicates.map { it.resetNanos.toDouble() }
            println(String.format("  envs=%-4d  reset=%,.0f ns  (%.1f ms)", envCount, median(resetValues), median(resetValues) / 1_000_000.0))
        }

        println("\n====== END PROFILING SWEEP ======\n")

        // Basic sanity: median SPS for 16 envs should be > 100
        val sps16 = results[16]!!.replicates.map { it.envSps }
        assertTrue(median(sps16) > 100.0, "Expected 16-env median SPS > 100, got ${median(sps16)}")
    }

    private data class ReplicateResult(
        val envSps: Double,
        val resetNanos: Long,
        val applyNanos: Long,
        val tickNanos: Long,
        val observeNanos: Long,
        val projectionNanos: Long,
        val totalNanos: Long,
        val lastTickStageNanos: LongArray,
    )

    private data class SweepResult(
        val envCount: Int,
        val replicates: List<ReplicateResult>,
        val medianApplyNanos: Long,
        val medianTickNanos: Long,
        val medianObserveNanos: Long,
        val medianProjectionNanos: Long,
        val lastTickStageNanos: LongArray,
    )

    private fun runReplicates(
        envCount: Int,
        warmupRounds: Int,
        measurementRounds: Int,
        replicateCount: Int,
    ): List<ReplicateResult> {
        val kernel = FastFightCavesKernelRuntime.createKernel(
            slotCount = envCount,
            tickCap = warmupRounds + measurementRounds + 64,
            accountNamePrefix = "profile_sweep_$envCount",
        )
        try {
            val waitActions = IntArray(envCount * FastActionCodec.PACKED_WORD_COUNT)
            return List(replicateCount) { repIdx ->
                val seedBase = 90_000L + (repIdx * 10_000L)
                val reset = kernel.resetBatch(
                    configs = List(envCount) { i -> FastEpisodeConfig(seed = seedBase + i, startWave = 1) },
                )
                repeat(warmupRounds) { kernel.stepBatch(waitActions) }

                var applyNanos = 0L
                var tickNanos = 0L
                var observeNanos = 0L
                var projectionNanos = 0L
                var totalNanos = 0L
                var lastStageNanos = LongArray(0)

                repeat(measurementRounds) {
                    val step = kernel.stepBatch(waitActions)
                    applyNanos += step.metrics.applyActionsNanos
                    tickNanos += step.metrics.tickNanos
                    observeNanos += step.metrics.observeFlatNanos
                    projectionNanos += step.metrics.projectionNanos
                    totalNanos += step.metrics.totalNanos
                    if (step.metrics.tickStageNanos.isNotEmpty()) {
                        lastStageNanos = step.metrics.tickStageNanos
                    }
                }

                val envSps = if (totalNanos > 0) envCount * measurementRounds * 1e9 / totalNanos else 0.0
                ReplicateResult(envSps, reset.elapsedNanos, applyNanos, tickNanos, observeNanos, projectionNanos, totalNanos, lastStageNanos)
            }
        } finally {
            kernel.close()
        }
    }

    private fun summarize(envCount: Int, replicates: List<ReplicateResult>): SweepResult {
        return SweepResult(
            envCount = envCount,
            replicates = replicates,
            medianApplyNanos = medianLong(replicates.map { it.applyNanos }),
            medianTickNanos = medianLong(replicates.map { it.tickNanos }),
            medianObserveNanos = medianLong(replicates.map { it.observeNanos }),
            medianProjectionNanos = medianLong(replicates.map { it.projectionNanos }),
            lastTickStageNanos = replicates.last().lastTickStageNanos,
        )
    }

    private fun median(values: List<Double>): Double = values.sorted()[values.size / 2]
    private fun medianLong(values: List<Long>): Long = values.sorted()[values.size / 2]
}
