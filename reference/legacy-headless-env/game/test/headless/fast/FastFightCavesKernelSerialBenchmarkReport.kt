import headless.fast.FastActionCodec
import headless.fast.FastEpisodeConfig
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlin.test.assertTrue

data class FastKernelSerialReplicate(
    val envStepsPerSecond: Double,
    val resetElapsedNanos: Long,
    val applyActionsNanos: Long,
    val tickNanos: Long,
    val observeFlatNanos: Long,
    val projectionNanos: Long,
    val totalNanos: Long,
)

data class FastKernelSerialRowSummary(
    val envCount: Int,
    val warmupRounds: Int,
    val measurementRounds: Int,
    val replicateCount: Int,
    val replicates: List<FastKernelSerialReplicate>,
    val medianEnvStepsPerSecond: Double,
    val minEnvStepsPerSecond: Double,
    val maxEnvStepsPerSecond: Double,
    val medianResetElapsedNanos: Long,
    val medianApplyActionsNanos: Long,
    val medianTickNanos: Long,
    val medianObserveFlatNanos: Long,
    val medianProjectionNanos: Long,
    val medianTotalNanos: Long,
)

data class FastKernelSerialBenchmarkReport(
    val artifactDir: String,
    val benchmarkPath: String,
    val warmupRounds: Int,
    val measurementRounds: Int,
    val replicateCount: Int,
    val rows: Map<String, FastKernelSerialRowSummary>,
)

internal object FastFightCavesKernelSerialBenchmarkReport {
    private const val DEFAULT_WARMUP_ROUNDS = 64
    private const val DEFAULT_MEASUREMENT_ROUNDS = 128
    private const val DEFAULT_REPLICATE_COUNT = 3

    fun writeDefaultReport(): Path {
        val workspaceRoot = locateRepositoryRoot().parent
        val dateStamp = LocalDate.now().toString().replace("-", "")
        val artifactDir =
            workspaceRoot.resolve("RL/artifacts/benchmarks/phase3_pr32_wsl_fast_serial_replicates_$dateStamp")
        Files.createDirectories(artifactDir)
        val report =
            runReport(
                artifactDir = artifactDir,
                envCounts = listOf(16, 64),
                warmupRounds = DEFAULT_WARMUP_ROUNDS,
                measurementRounds = DEFAULT_MEASUREMENT_ROUNDS,
                replicateCount = DEFAULT_REPLICATE_COUNT,
            )
        val outputPath = artifactDir.resolve("serial_replicate_summary.json")
        Files.writeString(outputPath, encodeJson(report))
        println("fast_kernel_serial_benchmark_artifact=$outputPath")
        return outputPath
    }

    private fun runReport(
        artifactDir: Path,
        envCounts: List<Int>,
        warmupRounds: Int,
        measurementRounds: Int,
        replicateCount: Int,
    ): FastKernelSerialBenchmarkReport {
        val rows =
            linkedMapOf<String, FastKernelSerialRowSummary>()
        for (envCount in envCounts) {
            resetHeadlessTestRuntime()
            val replicates = runReplicatesForEnvCount(envCount, warmupRounds, measurementRounds, replicateCount)
            rows["fast_kernel_$envCount"] =
                FastKernelSerialRowSummary(
                    envCount = envCount,
                    warmupRounds = warmupRounds,
                    measurementRounds = measurementRounds,
                    replicateCount = replicateCount,
                    replicates = replicates,
                    medianEnvStepsPerSecond = medianOf(replicates.map { it.envStepsPerSecond }),
                    minEnvStepsPerSecond = replicates.minOf { it.envStepsPerSecond },
                    maxEnvStepsPerSecond = replicates.maxOf { it.envStepsPerSecond },
                    medianResetElapsedNanos = medianOfLong(replicates.map { it.resetElapsedNanos }),
                    medianApplyActionsNanos = medianOfLong(replicates.map { it.applyActionsNanos }),
                    medianTickNanos = medianOfLong(replicates.map { it.tickNanos }),
                    medianObserveFlatNanos = medianOfLong(replicates.map { it.observeFlatNanos }),
                    medianProjectionNanos = medianOfLong(replicates.map { it.projectionNanos }),
                    medianTotalNanos = medianOfLong(replicates.map { it.totalNanos }),
                )
            resetHeadlessTestRuntime()
        }
        return FastKernelSerialBenchmarkReport(
            artifactDir = artifactDir.toString(),
            benchmarkPath = "FastFightCavesKernelRuntime.resetBatch + stepBatch + flat buffer",
            warmupRounds = warmupRounds,
            measurementRounds = measurementRounds,
            replicateCount = replicateCount,
            rows = rows,
        )
    }

    private fun runReplicatesForEnvCount(
        envCount: Int,
        warmupRounds: Int,
        measurementRounds: Int,
        replicateCount: Int,
    ): List<FastKernelSerialReplicate> {
        val kernel =
            FastFightCavesKernelRuntime.createKernel(
                slotCount = envCount,
                tickCap = warmupRounds + measurementRounds + 64,
                accountNamePrefix = "fast_bench_$envCount",
            )
        try {
            val waitActions = IntArray(envCount * FastActionCodec.PACKED_WORD_COUNT)
            return List(replicateCount) { replicateIndex ->
                val seedBase = 20_260_311L + (replicateIndex * 10_000L)
                val reset =
                    kernel.resetBatch(
                        configs =
                            List(envCount) { envIndex ->
                                FastEpisodeConfig(
                                    seed = seedBase + envIndex,
                                    startWave = 1,
                                )
                            },
                    )
                repeat(warmupRounds) {
                    kernel.stepBatch(waitActions)
                }

                var applyActionsNanos = 0L
                var tickNanos = 0L
                var observeFlatNanos = 0L
                var projectionNanos = 0L
                var totalNanos = 0L
                repeat(measurementRounds) {
                    val step = kernel.stepBatch(waitActions)
                    applyActionsNanos += step.metrics.applyActionsNanos
                    tickNanos += step.metrics.tickNanos
                    observeFlatNanos += step.metrics.observeFlatNanos
                    projectionNanos += step.metrics.projectionNanos
                    totalNanos += step.metrics.totalNanos
                }
                val envStepsPerSecond =
                    if (totalNanos <= 0L) {
                        0.0
                    } else {
                        envCount * measurementRounds * 1_000_000_000.0 / totalNanos.toDouble()
                    }
                FastKernelSerialReplicate(
                    envStepsPerSecond = envStepsPerSecond,
                    resetElapsedNanos = reset.elapsedNanos,
                    applyActionsNanos = applyActionsNanos,
                    tickNanos = tickNanos,
                    observeFlatNanos = observeFlatNanos,
                    projectionNanos = projectionNanos,
                    totalNanos = totalNanos,
                )
            }
        } finally {
            kernel.close()
        }
    }

    private fun medianOf(values: List<Double>): Double {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun medianOfLong(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun encodeJson(report: FastKernelSerialBenchmarkReport): String =
        buildString {
            appendLine("{")
            appendLine("  \"artifact_dir\": ${jsonString(report.artifactDir)},")
            appendLine("  \"benchmark_path\": ${jsonString(report.benchmarkPath)},")
            appendLine("  \"warmup_rounds\": ${report.warmupRounds},")
            appendLine("  \"measurement_rounds\": ${report.measurementRounds},")
            appendLine("  \"replicate_count\": ${report.replicateCount},")
            appendLine("  \"rows\": {")
            val rows = report.rows.entries.toList()
            rows.forEachIndexed { index, entry ->
                appendLine("    ${jsonString(entry.key)}: {")
                appendLine("      \"env_count\": ${entry.value.envCount},")
                appendLine("      \"warmup_rounds\": ${entry.value.warmupRounds},")
                appendLine("      \"measurement_rounds\": ${entry.value.measurementRounds},")
                appendLine("      \"replicate_count\": ${entry.value.replicateCount},")
                appendLine(
                    "      \"env_steps_per_second\": {\"median\": ${entry.value.medianEnvStepsPerSecond}, " +
                        "\"min\": ${entry.value.minEnvStepsPerSecond}, \"max\": ${entry.value.maxEnvStepsPerSecond}, " +
                        "\"values\": ${doubleArrayJson(entry.value.replicates.map { it.envStepsPerSecond })}},",
                )
                appendLine(
                    "      \"median_stage_totals_nanos\": {" +
                        "\"reset\": ${entry.value.medianResetElapsedNanos}, " +
                        "\"apply_actions\": ${entry.value.medianApplyActionsNanos}, " +
                        "\"tick\": ${entry.value.medianTickNanos}, " +
                        "\"observe_flat\": ${entry.value.medianObserveFlatNanos}, " +
                        "\"projection\": ${entry.value.medianProjectionNanos}, " +
                        "\"total\": ${entry.value.medianTotalNanos}" +
                        "},",
                )
                appendLine("      \"replicates\": [")
                entry.value.replicates.forEachIndexed { replicateIndex, replicate ->
                    append("        {")
                    append("\"env_steps_per_second\": ${replicate.envStepsPerSecond}, ")
                    append("\"reset_elapsed_nanos\": ${replicate.resetElapsedNanos}, ")
                    append("\"apply_actions_nanos\": ${replicate.applyActionsNanos}, ")
                    append("\"tick_nanos\": ${replicate.tickNanos}, ")
                    append("\"observe_flat_nanos\": ${replicate.observeFlatNanos}, ")
                    append("\"projection_nanos\": ${replicate.projectionNanos}, ")
                    append("\"total_nanos\": ${replicate.totalNanos}")
                    append("}")
                    if (replicateIndex != entry.value.replicates.lastIndex) {
                        append(",")
                    }
                    appendLine()
                }
                append("      ]")
                if (index != rows.lastIndex) {
                    appendLine()
                    appendLine("    },")
                } else {
                    appendLine()
                    appendLine("    }")
                }
            }
            appendLine("  }")
            appendLine("}")
        }

    private fun doubleArrayJson(values: List<Double>): String =
        values.joinToString(prefix = "[", postfix = "]")

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\") + "\""
}

internal class FastFightCavesKernelSerialBenchmarkReportTest {

    @org.junit.jupiter.api.AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @org.junit.jupiter.api.Test
    fun `fast kernel serial benchmark report is generated`() {
        val artifactPath = FastFightCavesKernelSerialBenchmarkReport.writeDefaultReport()
        assertTrue(Files.isRegularFile(artifactPath))
    }
}
