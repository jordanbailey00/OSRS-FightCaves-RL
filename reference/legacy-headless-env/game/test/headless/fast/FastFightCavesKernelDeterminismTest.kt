import headless.fast.FastEpisodeConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

internal class FastFightCavesKernelDeterminismTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `fast kernel reset and wait steps are deterministic for the same seeds`() {
        val kernel = FastFightCavesKernelRuntime.createKernel(slotCount = 2, tickCap = 32)
        val first =
            try {
                runScenario(kernel)
            } finally {
                // keep the runtime alive so determinism is measured against the same kernel lifecycle
            }
        val second =
            try {
                runScenario(kernel)
            } finally {
                kernel.close()
            }

        assertContentEquals(normalizeFlat(first.resetFlat), normalizeFlat(second.resetFlat))
        assertContentEquals(normalizeFlat(first.stepFlat), normalizeFlat(second.stepFlat))
        assertContentEquals(first.stepRewards, second.stepRewards)
        assertContentEquals(first.terminalCodes, second.terminalCodes)
        assertEquals(first.parityActionNames, second.parityActionNames)
    }

    private fun runScenario(kernel: FastFightCavesKernelRuntime): DeterminismCapture {
        val reset =
            kernel.resetBatch(
                configs =
                    listOf(
                        FastEpisodeConfig(seed = 707L, startWave = 1),
                        FastEpisodeConfig(seed = 808L, startWave = 1),
                    ),
            )
        val step =
            kernel.stepBatch(
                packedActions =
                    intArrayOf(
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                    ),
                emitParityTrace = true,
            )
        return DeterminismCapture(
            resetFlat = reset.flatObservations.copyOf(),
            stepFlat = step.flatObservations.copyOf(),
            stepRewards = step.rewardFeatures.copyOf(),
            terminalCodes = step.terminalCodes.copyOf(),
            parityActionNames = step.parityTraces.map { it.actionName },
        )
    }

    private data class DeterminismCapture(
        val resetFlat: FloatArray,
        val stepFlat: FloatArray,
        val stepRewards: FloatArray,
        val terminalCodes: IntArray,
        val parityActionNames: List<String>,
    )

    private fun normalizeFlat(values: FloatArray): FloatArray {
        val normalized = values.copyOf()
        val featureCount = 134
        val baseFieldCount = 30
        val npcFieldCount = 13
        val maxVisibleNpcs = 8
        for (envIndex in normalized.indices step featureCount) {
            normalized[envIndex + 1] = 0f
            normalized[envIndex + 3] = 0f
            normalized[envIndex + 4] = 0f
            normalized[envIndex + 5] = 0f
            for (slotIndex in 0 until maxVisibleNpcs) {
                val offset = envIndex + baseFieldCount + (slotIndex * npcFieldCount)
                normalized[offset + 1] = 0f
                normalized[offset + 2] = 0f
                normalized[offset + 4] = 0f
                normalized[offset + 5] = 0f
                normalized[offset + 6] = 0f
            }
        }
        return normalized
    }
}
