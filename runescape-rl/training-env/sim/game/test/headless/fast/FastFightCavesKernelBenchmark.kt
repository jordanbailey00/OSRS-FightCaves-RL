import headless.fast.FastActionCodec
import headless.fast.FastEpisodeConfig

data class FastFightCavesKernelBenchmarkResult(
    val envCount: Int,
    val steps: Int,
    val elapsedNanos: Long,
    val envStepsPerSecond: Double,
)

internal object FastFightCavesKernelBenchmark {

    fun runWaitBenchmark(
        envCount: Int,
        steps: Int,
        startWave: Int = 1,
    ): FastFightCavesKernelBenchmarkResult {
        val kernel = FastFightCavesKernelRuntime.createKernel(slotCount = envCount, tickCap = steps + 32)
        try {
            val configs =
                List(envCount) { envIndex ->
                    FastEpisodeConfig(
                        seed = 20_260_311L + envIndex,
                        startWave = startWave,
                    )
                }
            kernel.resetBatch(configs)
            val waitAction =
                IntArray(envCount * FastActionCodec.PACKED_WORD_COUNT) { index ->
                    if (index % FastActionCodec.PACKED_WORD_COUNT == 0) {
                        0
                    } else {
                        0
                    }
                }

            var elapsedNanos = 0L
            repeat(steps) {
                elapsedNanos += kernel.stepBatch(waitAction).metrics.totalNanos
            }
            val envStepsPerSecond =
                if (elapsedNanos <= 0L) {
                    0.0
                } else {
                    envCount * steps * 1_000_000_000.0 / elapsedNanos.toDouble()
                }
            return FastFightCavesKernelBenchmarkResult(
                envCount = envCount,
                steps = steps,
                elapsedNanos = elapsedNanos,
                envStepsPerSecond = envStepsPerSecond,
            )
        } finally {
            kernel.close()
        }
    }
}
