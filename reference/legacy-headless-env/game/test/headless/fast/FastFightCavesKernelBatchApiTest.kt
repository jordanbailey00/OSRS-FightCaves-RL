import headless.fast.FAST_REWARD_FEATURE_INVALID_ACTION_INDEX
import headless.fast.FAST_REWARD_FEATURE_TICK_PENALTY_BASE_INDEX
import headless.fast.FAST_TERMINAL_TICK_CAP
import headless.fast.FastActionCodec
import headless.fast.FastEpisodeConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FastFightCavesKernelBatchApiTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `fast kernel reset and step batches emit flat buffers reward features and parity traces`() {
        val kernel = FastFightCavesKernelRuntime.createKernel(slotCount = 2, tickCap = 32)
        try {
            val reset =
                kernel.resetBatch(
                    configs =
                        listOf(
                            FastEpisodeConfig(seed = 101L, startWave = 1),
                            FastEpisodeConfig(seed = 202L, startWave = 1),
                        ),
                    emitParityTrace = true,
                )
            assertEquals(2, reset.envCount)
            assertEquals(134, reset.flatObservationFeatureCount)
            assertEquals(16, reset.rewardFeatureCount)
            assertEquals(2 * 134, reset.flatObservations.size)
            assertEquals(2 * 16, reset.rewardFeatures.size)
            assertEquals(2, reset.parityTraces.size)
            assertContentEquals(intArrayOf(0, 1), reset.slotIndices)

            val step =
                kernel.stepBatch(
                    packedActions =
                        intArrayOf(
                            0, 0, 0, 0,
                            2, 99, 0, 0,
                        ),
                    emitParityTrace = true,
                )

            assertEquals(2, step.envCount)
            assertEquals(2, step.parityTraces.size)
            assertTrue(step.actionAccepted[0])
            assertFalse(step.actionAccepted[1])
            assertEquals(0, step.rejectionCodes[0])
            assertTrue(step.rejectionCodes[1] > 0)
            assertEquals(1f, step.rewardFeatures[FAST_REWARD_FEATURE_TICK_PENALTY_BASE_INDEX])
            assertEquals(1f, step.rewardFeatures[16 + FAST_REWARD_FEATURE_INVALID_ACTION_INDEX])
            assertEquals(step.terminalCodes[1], step.parityTraces[1].terminalCode)
            assertEquals("attack_visible_npc", step.parityTraces[1].actionName)
        } finally {
            kernel.close()
        }
    }

    @Test
    fun `fast kernel emits tick cap truncation directly in batch step output`() {
        val kernel = FastFightCavesKernelRuntime.createKernel(slotCount = 1, tickCap = 1)
        try {
            kernel.resetBatch(listOf(FastEpisodeConfig(seed = 404L, startWave = 1)))
            val step = kernel.stepBatch(FastActionCodec.encode(actionId = 0))
            assertFalse(step.terminated[0])
            assertTrue(step.truncated[0])
            assertEquals(FAST_TERMINAL_TICK_CAP, step.terminalCodes[0])
        } finally {
            kernel.close()
        }
    }
}
