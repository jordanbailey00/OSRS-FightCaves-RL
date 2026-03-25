import content.area.karamja.tzhaar_city.JadTelegraphTrace
import content.area.karamja.tzhaar_city.jadTelegraphTraceOrNull
import content.quest.instance
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.types.RandomDiagnostics
import sim.types.randomCallCount
import sim.types.randomDiagnostics

data class HeadlessReplayStep(
    val action: HeadlessAction,
    val ticksAfter: Int = 1,
)

data class HeadlessReplayTickSnapshot(
    val stepIndex: Int,
    val tick: Int,
    val action: HeadlessAction?,
    val actionResult: HeadlessActionResult?,
    val observation: HeadlessObservationV1,
    val jadTelegraph: JadTelegraphTrace?,
    val rngCallCount: Long,
)

data class HeadlessReplayResult(
    val seed: Long,
    val startWave: Int,
    val snapshots: List<HeadlessReplayTickSnapshot>,
    val rngDiagnostics: RandomDiagnostics,
)

class HeadlessReplayRunner(
    private val runtime: FightCaveSimulationRuntime,
) {

    fun run(
        player: Player,
        seed: Long,
        actionTrace: List<HeadlessReplayStep>,
        startWave: Int = 1,
        includeFutureLeakage: Boolean = false,
        stepHook: ((stepIndex: Int, step: HeadlessReplayStep?, player: Player) -> Unit)? = null,
    ): HeadlessReplayResult {
        require(startWave in 1..63) { "Fight Caves start wave must be in range 1..63, got $startWave." }
        for ((index, step) in actionTrace.withIndex()) {
            require(step.ticksAfter >= 0) { "Replay step $index has invalid ticksAfter=${step.ticksAfter}; expected >= 0." }
        }

        runtime.resetFightCaveEpisode(player, seed = seed, startWave = startWave)

        val snapshots = mutableListOf<HeadlessReplayTickSnapshot>()
        stepHook?.invoke(-1, null, player)
        snapshots += snapshot(runtime, player, includeFutureLeakage, stepIndex = -1, action = null, actionResult = null)

        for ((index, step) in actionTrace.withIndex()) {
            val actionResult = runtime.applyFightCaveAction(player, step.action)
            stepHook?.invoke(index, step, player)
            if (step.ticksAfter > 0) {
                runtime.tick(step.ticksAfter)
            }
            snapshots += snapshot(runtime, player, includeFutureLeakage, stepIndex = index, action = step.action, actionResult = actionResult)
        }

        return HeadlessReplayResult(
            seed = seed,
            startWave = startWave,
            snapshots = snapshots,
            rngDiagnostics = randomDiagnostics(),
        )
    }

    private fun snapshot(
        runtime: FightCaveSimulationRuntime,
        player: Player,
        includeFutureLeakage: Boolean,
        stepIndex: Int,
        action: HeadlessAction?,
        actionResult: HeadlessActionResult?,
    ): HeadlessReplayTickSnapshot {
        val observation = runtime.observeFightCave(player, includeFutureLeakage = includeFutureLeakage)
        return HeadlessReplayTickSnapshot(
            stepIndex = stepIndex,
            tick = observation.tick,
            action = action,
            actionResult = actionResult,
            observation = observation,
            jadTelegraph = currentJadTelegraph(player),
            rngCallCount = randomCallCount(),
        )
    }

    private fun currentJadTelegraph(player: Player): JadTelegraphTrace? = fightCaveNpcs(player).firstNotNullOfOrNull { it.jadTelegraphTraceOrNull() }

    private fun fightCaveNpcs(player: Player): List<NPC> {
        val instance = player.instance()
        return if (instance != null) {
            buildList {
                for (level in 0..3) {
                    addAll(NPCs.at(instance.toLevel(level)))
                }
            }
        } else {
            NPCs.at(player.tile.regionLevel).toList()
        }
    }
}
