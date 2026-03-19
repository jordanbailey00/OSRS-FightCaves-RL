import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.system.measureNanoTime

object HeadlessPerformanceReportGenerator {

    @JvmStatic
    fun main(args: Array<String>) {
        val options = parseArgs(args)
        val outputs =
            generateAndWriteArtifacts(
                outputLog = options.outputLog,
                outputJson = options.outputJson,
                config = options.config,
            )
        println("Performance benchmark log written to ${outputs.first}")
        println("Performance benchmark json written to ${outputs.second}")
    }

    fun generateAndWriteLog(output: Path = defaultLogPath()): Path {
        generateAndWriteArtifacts(outputLog = output, outputJson = siblingJsonPath(output))
        return output
    }

    fun generateAndWriteArtifacts(
        outputLog: Path = defaultLogPath(),
        outputJson: Path = defaultJsonPath(),
        config: PerformanceBenchmarkConfig = PerformanceBenchmarkConfig(),
    ): Pair<Path, Path> {
        val report = runBenchmarkSuite(config)
        writeLog(outputLog, report)
        writeJson(outputJson, report)
        return outputLog to outputJson
    }

    fun runBenchmarkSuite(config: PerformanceBenchmarkConfig = PerformanceBenchmarkConfig()): PerformanceBenchmarkSuiteResult {
        require(config.throughputSteps > 0) { "throughputSteps must be > 0, got ${config.throughputSteps}." }
        require(config.soakSteps > 0) { "soakSteps must be > 0, got ${config.soakSteps}." }
        require(config.batchedEnvCount > 0) { "batchedEnvCount must be > 0, got ${config.batchedEnvCount}." }
        require(config.batchedRounds > 0) { "batchedRounds must be > 0, got ${config.batchedRounds}." }

        val timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val repoRoot = locateRepositoryRoot()
        val runtimeMetadata = buildRuntimeMetadata()

        val runtime = bootstrapHeadlessWithScripts(startWorld = false)
        return try {
            val singlePlayer = createHeadlessPlayer("perf-suite-single")
            val throughputResult = runSingleSlotBenchmark(runtime, singlePlayer, config)
            val soakResult = runSoakBenchmark(runtime, singlePlayer, config)
            val batchedResult = runBatchedBenchmark(runtime, config)

            PerformanceBenchmarkSuiteResult(
                generatedAtUtc = timestamp,
                repoCommitSha = resolveGitCommitSha(repoRoot),
                runtimeMetadata = runtimeMetadata,
                benchmarkConfig = config,
                throughput = throughputResult,
                batched = batchedResult,
                soak = soakResult,
                perWorkerCeiling = buildPerWorkerCeiling(config, throughputResult, batchedResult),
            )
        } finally {
            runtime.shutdown()
            resetHeadlessTestRuntime()
        }
    }

    private fun runSingleSlotBenchmark(
        runtime: FightCaveSimulationRuntime,
        player: sim.engine.entity.character.player.Player,
        config: PerformanceBenchmarkConfig,
    ): ThroughputBenchmarkResult {
        runtime.resetFightCaveEpisode(player, seed = config.seedBase, startWave = config.startWave)
        val steps = List(config.throughputSteps) { HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 1) }
        val result = runtime.runFightCaveBatch(player, steps, observeEvery = 0)
        return ThroughputBenchmarkResult(
            steps = result.steps,
            ticksAdvanced = result.ticksAdvanced,
            elapsedNanos = result.elapsedNanos,
            stepsPerSecond = result.stepsPerSecond,
            ticksPerSecond = result.ticksPerSecond,
        )
    }

    private fun runSoakBenchmark(
        runtime: FightCaveSimulationRuntime,
        player: sim.engine.entity.character.player.Player,
        config: PerformanceBenchmarkConfig,
    ): SoakBenchmarkResult {
        runtime.resetFightCaveEpisode(player, seed = config.seedBase + 11_110L, startWave = config.startWave)
        val steps = List(config.soakSteps) { HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 1) }
        val result = runtime.runFightCaveBatch(player, steps, observeEvery = config.soakObserveEvery)
        val final = result.finalObservation
        return SoakBenchmarkResult(
            steps = result.steps,
            ticksAdvanced = result.ticksAdvanced,
            elapsedNanos = result.elapsedNanos,
            ticksPerSecond = result.ticksPerSecond,
            finalTick = final.tick,
            finalWave = final.wave.wave,
            finalRemaining = final.wave.remaining,
            finalHitpoints = final.player.hitpointsCurrent,
            finalPrayer = final.player.prayerCurrent,
        )
    }

    private fun runBatchedBenchmark(
        runtime: FightCaveSimulationRuntime,
        config: PerformanceBenchmarkConfig,
    ): BatchedThroughputBenchmarkResult {
        val players =
            List(config.batchedEnvCount) { index ->
                createHeadlessPlayer("perf-suite-batch-$index")
            }
        players.forEachIndexed { index, player ->
            runtime.resetFightCaveEpisode(
                player,
                seed = config.seedBase + 20_000L + index,
                startWave = config.startWave,
            )
        }

        val elapsedNanos =
            measureNanoTime {
                repeat(config.batchedRounds) {
                    runtime.tick(1)
                }
            }
        val finalObservations = players.map(runtime::observeFightCave)
        val totalEnvSteps = config.batchedEnvCount * config.batchedRounds
        val envStepsPerSecond =
            if (elapsedNanos <= 0L || totalEnvSteps <= 0) {
                0.0
            } else {
                totalEnvSteps * 1_000_000_000.0 / elapsedNanos.toDouble()
            }
        val tickRoundsPerSecond =
            if (elapsedNanos <= 0L || config.batchedRounds <= 0) {
                0.0
            } else {
                config.batchedRounds * 1_000_000_000.0 / elapsedNanos.toDouble()
            }
        return BatchedThroughputBenchmarkResult(
            envCount = config.batchedEnvCount,
            tickRounds = config.batchedRounds,
            totalEnvSteps = totalEnvSteps,
            elapsedNanos = elapsedNanos,
            envStepsPerSecond = envStepsPerSecond,
            tickRoundsPerSecond = tickRoundsPerSecond,
            minFinalWave = finalObservations.minOf { it.wave.wave },
            maxFinalWave = finalObservations.maxOf { it.wave.wave },
            minFinalHitpoints = finalObservations.minOf { it.player.hitpointsCurrent },
            minFinalPrayer = finalObservations.minOf { it.player.prayerCurrent },
        )
    }

    private fun buildPerWorkerCeiling(
        config: PerformanceBenchmarkConfig,
        throughput: ThroughputBenchmarkResult,
        batched: BatchedThroughputBenchmarkResult,
    ): PerWorkerCeilingEstimate {
        val singleSlotTicksPerSecond = throughput.ticksPerSecond
        val batchedEnvStepsPerSecond = batched.envStepsPerSecond
        val workersNeededFor100k =
            if (batchedEnvStepsPerSecond <= 0.0) {
                null
            } else {
                ceil(100_000.0 / batchedEnvStepsPerSecond).toInt()
            }
        return PerWorkerCeilingEstimate(
            singleSlotTicksPerSecond = singleSlotTicksPerSecond,
            batchedEnvCount = config.batchedEnvCount,
            batchedEnvStepsPerSecond = batchedEnvStepsPerSecond,
            workersNeededFor100k = workersNeededFor100k,
        )
    }

    private fun writeLog(path: Path, report: PerformanceBenchmarkSuiteResult) {
        Files.createDirectories(path.parent)
        val log =
            buildString {
                appendLine("generated_at_utc=${report.generatedAtUtc}")
                appendLine("repo_commit_sha=${report.repoCommitSha}")
                appendLine("runtime.host_class=${report.runtimeMetadata.hostClass}")
                appendLine("runtime.performance_source_of_truth=${report.runtimeMetadata.performanceSourceOfTruth}")
                appendLine("runtime.os_name=${report.runtimeMetadata.osName}")
                appendLine("runtime.os_version=${report.runtimeMetadata.osVersion}")
                appendLine("runtime.java_runtime_version=${report.runtimeMetadata.javaRuntimeVersion}")
                appendLine("runtime.java_vm_name=${report.runtimeMetadata.javaVmName}")
                appendLine("runtime.available_processors=${report.runtimeMetadata.availableProcessors}")
                appendLine("config.start_wave=${report.benchmarkConfig.startWave}")
                appendLine("config.seed_base=${report.benchmarkConfig.seedBase}")
                appendLine("config.throughput_steps=${report.benchmarkConfig.throughputSteps}")
                appendLine("config.soak_steps=${report.benchmarkConfig.soakSteps}")
                appendLine("config.batched_env_count=${report.benchmarkConfig.batchedEnvCount}")
                appendLine("config.batched_rounds=${report.benchmarkConfig.batchedRounds}")
                appendLine("throughput.steps=${report.throughput.steps}")
                appendLine("throughput.ticks_advanced=${report.throughput.ticksAdvanced}")
                appendLine("throughput.elapsed_nanos=${report.throughput.elapsedNanos}")
                appendLine("throughput.steps_per_second=${"%.2f".format(report.throughput.stepsPerSecond)}")
                appendLine("throughput.ticks_per_second=${"%.2f".format(report.throughput.ticksPerSecond)}")
                appendLine("batched.env_count=${report.batched.envCount}")
                appendLine("batched.tick_rounds=${report.batched.tickRounds}")
                appendLine("batched.total_env_steps=${report.batched.totalEnvSteps}")
                appendLine("batched.elapsed_nanos=${report.batched.elapsedNanos}")
                appendLine("batched.env_steps_per_second=${"%.2f".format(report.batched.envStepsPerSecond)}")
                appendLine("batched.tick_rounds_per_second=${"%.2f".format(report.batched.tickRoundsPerSecond)}")
                appendLine("batched.min_final_wave=${report.batched.minFinalWave}")
                appendLine("batched.max_final_wave=${report.batched.maxFinalWave}")
                appendLine("batched.min_final_hitpoints=${report.batched.minFinalHitpoints}")
                appendLine("batched.min_final_prayer=${report.batched.minFinalPrayer}")
                appendLine("soak.steps=${report.soak.steps}")
                appendLine("soak.ticks_advanced=${report.soak.ticksAdvanced}")
                appendLine("soak.elapsed_nanos=${report.soak.elapsedNanos}")
                appendLine("soak.ticks_per_second=${"%.2f".format(report.soak.ticksPerSecond)}")
                appendLine("soak.final_tick=${report.soak.finalTick}")
                appendLine("soak.final_wave=${report.soak.finalWave}")
                appendLine("soak.final_remaining=${report.soak.finalRemaining}")
                appendLine("soak.final_hitpoints=${report.soak.finalHitpoints}")
                appendLine("soak.final_prayer=${report.soak.finalPrayer}")
                appendLine("ceiling.single_slot_ticks_per_second=${"%.2f".format(report.perWorkerCeiling.singleSlotTicksPerSecond)}")
                appendLine("ceiling.batched_env_steps_per_second=${"%.2f".format(report.perWorkerCeiling.batchedEnvStepsPerSecond)}")
                appendLine("ceiling.workers_needed_for_100k=${report.perWorkerCeiling.workersNeededFor100k ?: "unknown"}")
            }
        Files.writeString(path, log)
    }

    private fun writeJson(path: Path, report: PerformanceBenchmarkSuiteResult) {
        Files.createDirectories(path.parent)
        Files.writeString(path, jsonString(report.toMap()) + "\n")
    }

    private fun buildRuntimeMetadata(): RuntimeMetadata {
        val osName = System.getProperty("os.name")
        val osVersion = System.getProperty("os.version")
        val javaVmName = System.getProperty("java.vm.name")
        val javaRuntimeVersion = System.getProperty("java.runtime.version")
        val hostClass =
            when {
                osName.equals("Linux", ignoreCase = true) &&
                    osVersion.contains("microsoft-standard-wsl", ignoreCase = true) -> "wsl2"
                osName.equals("Linux", ignoreCase = true) -> "linux_native"
                osName.startsWith("Windows", ignoreCase = true) -> "windows"
                else -> "other"
            }
        return RuntimeMetadata(
            osName = osName,
            osVersion = osVersion,
            javaRuntimeVersion = javaRuntimeVersion,
            javaVmName = javaVmName,
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            maxMemoryBytes = Runtime.getRuntime().maxMemory(),
            hostClass = hostClass,
            performanceSourceOfTruth = hostClass == "linux_native" || hostClass == "wsl2",
            jvmInputArguments = ManagementFactory.getRuntimeMXBean().inputArguments.toList(),
        )
    }

    private fun resolveGitCommitSha(repoRoot: Path): String {
        val process =
            ProcessBuilder("git", "-C", repoRoot.toString(), "rev-parse", "HEAD")
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = process.waitFor()
        return if (exitCode == 0 && output.isNotBlank()) output else "unknown"
    }

    private fun defaultLogPath(): Path = locateRepositoryRoot().resolve("history/performance_benchmark.log")

    private fun defaultJsonPath(): Path = locateRepositoryRoot().resolve("history/performance_benchmark.json")

    private fun siblingJsonPath(path: Path): Path {
        val fileName = path.fileName.toString()
        return path.resolveSibling(fileName.substringBeforeLast('.', fileName) + ".json")
    }

    private fun locateRepositoryRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (true) {
            if (
                Files.isRegularFile(current.resolve("settings.gradle.kts")) &&
                    Files.isDirectory(current.resolve("game")) &&
                    Files.isDirectory(current.resolve("docs"))
            ) {
                return current
            }
            current = current.parent ?: break
        }
        error("Unable to locate repository root from ${Path.of("").toAbsolutePath().normalize()}.")
    }

    private fun parseArgs(args: Array<String>): CliOptions {
        var outputLog = defaultLogPath()
        var outputJson = defaultJsonPath()
        var throughputSteps = 10_000
        var soakSteps = 20_000
        var soakObserveEvery = 500
        var batchedEnvCount = 16
        var batchedRounds = 4_000
        var startWave = 1
        var seedBase = 11_110L

        var index = 0
        while (index < args.size) {
            when (val key = args[index]) {
                "--output-log" -> {
                    outputLog = Path.of(args.getOrElse(index + 1) { error("Missing value for $key") })
                    index += 2
                }
                "--output-json" -> {
                    outputJson = Path.of(args.getOrElse(index + 1) { error("Missing value for $key") })
                    index += 2
                }
                "--throughput-steps" -> {
                    throughputSteps = args.getOrElse(index + 1) { error("Missing value for $key") }.toInt()
                    index += 2
                }
                "--soak-steps" -> {
                    soakSteps = args.getOrElse(index + 1) { error("Missing value for $key") }.toInt()
                    index += 2
                }
                "--soak-observe-every" -> {
                    soakObserveEvery = args.getOrElse(index + 1) { error("Missing value for $key") }.toInt()
                    index += 2
                }
                "--batched-env-count" -> {
                    batchedEnvCount = args.getOrElse(index + 1) { error("Missing value for $key") }.toInt()
                    index += 2
                }
                "--batched-rounds" -> {
                    batchedRounds = args.getOrElse(index + 1) { error("Missing value for $key") }.toInt()
                    index += 2
                }
                "--start-wave" -> {
                    startWave = args.getOrElse(index + 1) { error("Missing value for $key") }.toInt()
                    index += 2
                }
                "--seed-base" -> {
                    seedBase = args.getOrElse(index + 1) { error("Missing value for $key") }.toLong()
                    index += 2
                }
                else -> error("Unrecognized argument: $key")
            }
        }

        return CliOptions(
            outputLog = outputLog,
            outputJson = outputJson,
            config =
                PerformanceBenchmarkConfig(
                    throughputSteps = throughputSteps,
                    soakSteps = soakSteps,
                    soakObserveEvery = soakObserveEvery,
                    batchedEnvCount = batchedEnvCount,
                    batchedRounds = batchedRounds,
                    startWave = startWave,
                    seedBase = seedBase,
                ),
        )
    }
}

data class CliOptions(
    val outputLog: Path,
    val outputJson: Path,
    val config: PerformanceBenchmarkConfig,
)

data class PerformanceBenchmarkConfig(
    val throughputSteps: Int = 10_000,
    val soakSteps: Int = 20_000,
    val soakObserveEvery: Int = 500,
    val batchedEnvCount: Int = 16,
    val batchedRounds: Int = 4_000,
    val startWave: Int = 1,
    val seedBase: Long = 11_110L,
) {
    fun toMap(): Map<String, Any?> =
        linkedMapOf(
            "throughput_steps" to throughputSteps,
            "soak_steps" to soakSteps,
            "soak_observe_every" to soakObserveEvery,
            "batched_env_count" to batchedEnvCount,
            "batched_rounds" to batchedRounds,
            "start_wave" to startWave,
            "seed_base" to seedBase,
        )
}

data class PerformanceBenchmarkSuiteResult(
    val generatedAtUtc: String,
    val repoCommitSha: String,
    val runtimeMetadata: RuntimeMetadata,
    val benchmarkConfig: PerformanceBenchmarkConfig,
    val throughput: ThroughputBenchmarkResult,
    val batched: BatchedThroughputBenchmarkResult,
    val soak: SoakBenchmarkResult,
    val perWorkerCeiling: PerWorkerCeilingEstimate,
) {
    fun toMap(): Map<String, Any?> =
        linkedMapOf(
            "generated_at_utc" to generatedAtUtc,
            "repo_commit_sha" to repoCommitSha,
            "runtime_metadata" to runtimeMetadata.toMap(),
            "benchmark_config" to benchmarkConfig.toMap(),
            "throughput" to throughput.toMap(),
            "batched" to batched.toMap(),
            "soak" to soak.toMap(),
            "per_worker_ceiling" to perWorkerCeiling.toMap(),
        )
}

data class RuntimeMetadata(
    val osName: String,
    val osVersion: String,
    val javaRuntimeVersion: String,
    val javaVmName: String,
    val availableProcessors: Int,
    val maxMemoryBytes: Long,
    val hostClass: String,
    val performanceSourceOfTruth: Boolean,
    val jvmInputArguments: List<String>,
) {
    fun toMap(): Map<String, Any?> =
        linkedMapOf(
            "os_name" to osName,
            "os_version" to osVersion,
            "java_runtime_version" to javaRuntimeVersion,
            "java_vm_name" to javaVmName,
            "available_processors" to availableProcessors,
            "max_memory_bytes" to maxMemoryBytes,
            "host_class" to hostClass,
            "performance_source_of_truth" to performanceSourceOfTruth,
            "jvm_input_arguments" to jvmInputArguments,
        )
}

data class ThroughputBenchmarkResult(
    val steps: Int,
    val ticksAdvanced: Int,
    val elapsedNanos: Long,
    val stepsPerSecond: Double,
    val ticksPerSecond: Double,
) {
    fun toMap(): Map<String, Any?> =
        linkedMapOf(
            "steps" to steps,
            "ticks_advanced" to ticksAdvanced,
            "elapsed_nanos" to elapsedNanos,
            "steps_per_second" to stepsPerSecond,
            "ticks_per_second" to ticksPerSecond,
        )
}

data class BatchedThroughputBenchmarkResult(
    val envCount: Int,
    val tickRounds: Int,
    val totalEnvSteps: Int,
    val elapsedNanos: Long,
    val envStepsPerSecond: Double,
    val tickRoundsPerSecond: Double,
    val minFinalWave: Int,
    val maxFinalWave: Int,
    val minFinalHitpoints: Int,
    val minFinalPrayer: Int,
) {
    fun toMap(): Map<String, Any?> =
        linkedMapOf(
            "env_count" to envCount,
            "tick_rounds" to tickRounds,
            "total_env_steps" to totalEnvSteps,
            "elapsed_nanos" to elapsedNanos,
            "env_steps_per_second" to envStepsPerSecond,
            "tick_rounds_per_second" to tickRoundsPerSecond,
            "min_final_wave" to minFinalWave,
            "max_final_wave" to maxFinalWave,
            "min_final_hitpoints" to minFinalHitpoints,
            "min_final_prayer" to minFinalPrayer,
        )
}

data class SoakBenchmarkResult(
    val steps: Int,
    val ticksAdvanced: Int,
    val elapsedNanos: Long,
    val ticksPerSecond: Double,
    val finalTick: Int,
    val finalWave: Int,
    val finalRemaining: Int,
    val finalHitpoints: Int,
    val finalPrayer: Int,
) {
    fun toMap(): Map<String, Any?> =
        linkedMapOf(
            "steps" to steps,
            "ticks_advanced" to ticksAdvanced,
            "elapsed_nanos" to elapsedNanos,
            "ticks_per_second" to ticksPerSecond,
            "final_tick" to finalTick,
            "final_wave" to finalWave,
            "final_remaining" to finalRemaining,
            "final_hitpoints" to finalHitpoints,
            "final_prayer" to finalPrayer,
        )
}

data class PerWorkerCeilingEstimate(
    val singleSlotTicksPerSecond: Double,
    val batchedEnvCount: Int,
    val batchedEnvStepsPerSecond: Double,
    val workersNeededFor100k: Int?,
) {
    fun toMap(): Map<String, Any?> =
        linkedMapOf(
            "single_slot_ticks_per_second" to singleSlotTicksPerSecond,
            "batched_env_count" to batchedEnvCount,
            "batched_env_steps_per_second" to batchedEnvStepsPerSecond,
            "workers_needed_for_100k" to workersNeededFor100k,
        )
}

private fun jsonString(value: Any?): String =
    when (value) {
        null -> "null"
        is String -> "\"" + escapeJson(value) + "\""
        is Number, is Boolean -> value.toString()
        is Map<*, *> ->
            value.entries.joinToString(prefix = "{", postfix = "}") { entry ->
                jsonString(entry.key.toString()) + ":" + jsonString(entry.value)
            }
        is Iterable<*> ->
            value.joinToString(prefix = "[", postfix = "]") { item ->
                jsonString(item)
            }
        else -> jsonString(value.toString())
    }

private fun escapeJson(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
