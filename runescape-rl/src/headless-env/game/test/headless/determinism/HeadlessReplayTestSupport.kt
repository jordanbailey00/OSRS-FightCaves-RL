internal fun deterministicFightCaveTrace(): List<HeadlessReplayStep> =
    listOf(
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 8),
        HeadlessReplayStep(HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMagic), ticksAfter = 1),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 40),
        HeadlessReplayStep(HeadlessAction.EatShark, ticksAfter = 2),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 60),
    )

internal fun executeHeadlessReplay(seed: Long, trace: List<HeadlessReplayStep>, playerName: String): HeadlessReplayResult {
    val runtime = bootstrapHeadlessWithScripts(startWorld = true)
    return try {
        val player = createHeadlessPlayer(playerName)
        val runner = HeadlessReplayRunner(runtime)
        runner.run(player = player, seed = seed, actionTrace = trace, startWave = 1)
    } finally {
        runtime.shutdown()
        resetHeadlessTestRuntime()
    }
}
