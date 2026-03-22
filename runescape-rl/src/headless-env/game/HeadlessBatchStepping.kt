import sim.engine.entity.character.player.Player
import kotlin.system.measureNanoTime

data class HeadlessBatchStepSnapshot(
    val stepIndex: Int,
    val tick: Int,
    val actionResult: HeadlessActionResult,
    val observation: HeadlessObservationV1,
)

data class HeadlessBatchRunResult(
    val steps: Int,
    val ticksAdvanced: Int,
    val actionAppliedCount: Int,
    val elapsedNanos: Long,
    val snapshots: List<HeadlessBatchStepSnapshot>,
    val finalObservation: HeadlessObservationV1,
) {
    val elapsedMillis: Double
        get() = elapsedNanos / 1_000_000.0

    val stepsPerSecond: Double
        get() =
            if (elapsedNanos <= 0L || steps <= 0) {
                0.0
            } else {
                steps * 1_000_000_000.0 / elapsedNanos.toDouble()
            }

    val ticksPerSecond: Double
        get() =
            if (elapsedNanos <= 0L || ticksAdvanced <= 0) {
                0.0
            } else {
                ticksAdvanced * 1_000_000_000.0 / elapsedNanos.toDouble()
            }
}

fun FightCaveSimulationRuntime.runFightCaveBatch(
    player: Player,
    steps: List<HeadlessReplayStep>,
    observeEvery: Int = 0,
    includeFutureLeakage: Boolean = false,
): HeadlessBatchRunResult {
    require(observeEvery >= 0) { "observeEvery must be >= 0, got $observeEvery." }
    for ((index, step) in steps.withIndex()) {
        require(step.ticksAfter >= 0) { "Batch step $index has invalid ticksAfter=${step.ticksAfter}; expected >= 0." }
    }

    val snapshots = if (observeEvery > 0) mutableListOf<HeadlessBatchStepSnapshot>() else mutableListOf()
    var ticksAdvanced = 0
    var actionAppliedCount = 0

    val elapsedNanos =
        measureNanoTime {
            for ((index, step) in steps.withIndex()) {
                val actionResult = applyFightCaveAction(player, step.action)
                if (actionResult.actionApplied) {
                    actionAppliedCount++
                }
                if (step.ticksAfter > 0) {
                    tick(step.ticksAfter)
                    ticksAdvanced += step.ticksAfter
                }

                if (observeEvery > 0 && ((index + 1) % observeEvery == 0 || index == steps.lastIndex)) {
                    val observation = observeFightCave(player, includeFutureLeakage = includeFutureLeakage)
                    snapshots +=
                        HeadlessBatchStepSnapshot(
                            stepIndex = index,
                            tick = observation.tick,
                            actionResult = actionResult,
                            observation = observation,
                        )
                }
            }
        }

    val finalObservation = snapshots.lastOrNull()?.observation ?: observeFightCave(player, includeFutureLeakage = includeFutureLeakage)

    return HeadlessBatchRunResult(
        steps = steps.size,
        ticksAdvanced = ticksAdvanced,
        actionAppliedCount = actionAppliedCount,
        elapsedNanos = elapsedNanos,
        snapshots = snapshots,
        finalObservation = finalObservation,
    )
}