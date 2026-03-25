import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class HeadlessBatchSteppingParityTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `batch stepping matches sequential stepping final state and action outcomes`() {
        val seed = 515151L
        val trace =
            listOf(
                HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 2),
                HeadlessReplayStep(HeadlessAction.AttackVisibleNpc(0), ticksAfter = 6),
                HeadlessReplayStep(HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMissiles), ticksAfter = 1),
                HeadlessReplayStep(HeadlessAction.EatShark, ticksAfter = 3),
                HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 4),
            )

        val sequential = runSequential(seed, trace)
        val batch = runBatch(seed, trace)

        assertEquals(sequential.ticksAdvanced, batch.ticksAdvanced)

        val sequentialMap = sequential.finalObservation.toOrderedMap().toMutableMap().apply { remove("tick") }
        val batchMap = batch.finalObservation.toOrderedMap().toMutableMap().apply { remove("tick") }
        assertEquals(sequentialMap, batchMap)
        assertEquals(sequential.outcomes, batch.outcomes)
    }

    private fun runSequential(seed: Long, trace: List<HeadlessReplayStep>): RunResult {
        val runtime = bootstrapHeadlessWithScripts(startWorld = false)
        return try {
            val player = createHeadlessPlayer("batch-parity-sequential")
            runtime.resetFightCaveEpisode(player, seed = seed, startWave = 1)

            val startTick = runtime.observeFightCave(player).tick
            val outcomes = mutableListOf<Pair<Boolean, HeadlessActionRejectReason?>>()
            for (step in trace) {
                val actionResult = runtime.applyFightCaveAction(player, step.action)
                outcomes += actionResult.actionApplied to actionResult.rejectionReason
                if (step.ticksAfter > 0) {
                    runtime.tick(step.ticksAfter)
                }
            }
            val finalObservation = runtime.observeFightCave(player)
            val ticksAdvanced = finalObservation.tick - startTick
            RunResult(finalObservation, outcomes, ticksAdvanced)
        } finally {
            runtime.shutdown()
            resetHeadlessTestRuntime()
        }
    }

    private fun runBatch(seed: Long, trace: List<HeadlessReplayStep>): RunResult {
        val runtime = bootstrapHeadlessWithScripts(startWorld = false)
        return try {
            val player = createHeadlessPlayer("batch-parity-batch")
            runtime.resetFightCaveEpisode(player, seed = seed, startWave = 1)
            val batch = runtime.runFightCaveBatch(player, trace, observeEvery = 1)
            val outcomes = batch.snapshots.map { it.actionResult.actionApplied to it.actionResult.rejectionReason }
            RunResult(batch.finalObservation, outcomes, batch.ticksAdvanced)
        } finally {
            runtime.shutdown()
            resetHeadlessTestRuntime()
        }
    }

    private data class RunResult(
        val finalObservation: HeadlessObservationV1,
        val outcomes: List<Pair<Boolean, HeadlessActionRejectReason?>>,
        val ticksAdvanced: Int,
    )
}
