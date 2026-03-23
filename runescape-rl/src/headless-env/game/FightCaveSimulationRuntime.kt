import sim.engine.entity.character.player.Player

interface FightCaveSimulationRuntime {
    fun tick(times: Int = 1)

    fun resetFightCaveEpisode(player: Player, config: FightCaveEpisodeConfig): FightCaveEpisodeState

    fun resetFightCaveEpisode(player: Player, seed: Long, startWave: Int = 1): FightCaveEpisodeState

    fun visibleFightCaveNpcTargets(player: Player): List<HeadlessVisibleNpcTarget>

    fun applyFightCaveAction(player: Player, action: HeadlessAction): HeadlessActionResult

    fun applyActionsBatch(players: List<Player>, actions: List<HeadlessAction>): List<HeadlessActionResult> {
        require(players.size == actions.size) {
            "Batch action application requires player/action parity: ${players.size} != ${actions.size}."
        }
        return players.zip(actions).map { (player, action) ->
            applyFightCaveAction(player, action)
        }
    }

    fun observeFightCave(player: Player, includeFutureLeakage: Boolean = false): HeadlessObservationV1

    fun observeFightCaveFlat(player: Player): HeadlessTrainingFlatObservationV1

    fun observeFlatBatch(players: List<Player>): HeadlessTrainingFlatObservationBatchV1 =
        packFlatObservationBatch(players.map(::observeFightCaveFlat))

    fun shutdown()
}
