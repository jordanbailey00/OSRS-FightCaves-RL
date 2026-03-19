object DemoLiteWaveLogic {
    fun evaluateTerminalCode(
        observation: HeadlessObservationV1,
        inFightCaveInstance: Boolean,
        episodeStartTick: Int,
        tickCap: Int,
    ): DemoLiteTerminalCode =
        when {
            observation.player.hitpointsCurrent <= 0 -> DemoLiteTerminalCode.PLAYER_DEATH
            observation.wave.wave == 63 && observation.wave.remaining == 0 -> DemoLiteTerminalCode.CAVE_COMPLETE
            !inFightCaveInstance || observation.wave.wave !in 1..63 -> DemoLiteTerminalCode.INVALID_STATE
            observation.tick - episodeStartTick >= tickCap -> DemoLiteTerminalCode.TICK_CAP
            else -> DemoLiteTerminalCode.NONE
        }

    fun describeWave(observation: HeadlessObservationV1): String =
        "Wave ${observation.wave.wave}/63 | Remaining ${observation.wave.remaining}"
}
