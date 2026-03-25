import com.github.michaelbull.logging.InlineLogger
import content.area.karamja.tzhaar_city.TzhaarFightCave
import sim.cache.Cache
import sim.engine.GameLoop
import sim.engine.Script
import sim.engine.data.ConfigFiles
import sim.engine.data.Settings
import sim.engine.data.configFiles
import sim.engine.entity.World
import sim.engine.entity.character.player.Player
import sim.engine.event.AuditLog
import java.util.*

data class OracleRuntime(
    val settings: Properties,
    val configFiles: ConfigFiles,
    val loadedScriptClasses: Set<String>,
    val loadedScripts: List<Script>,
    val stages: List<Runnable>,
    val gameLoop: GameLoop,
    val episodeInitializer: FightCaveEpisodeInitializer?,
    val actionAdapter: HeadlessActionAdapter?,
    val observationBuilder: HeadlessObservationBuilder?,
    val flatObservationBuilder: HeadlessFlatObservationBuilder?,
) : FightCaveSimulationRuntime {

    override fun tick(times: Int) {
        repeat(times) {
            GameLoop.tick++
            gameLoop.tick()
        }
    }

    override fun resetFightCaveEpisode(player: Player, config: FightCaveEpisodeConfig): FightCaveEpisodeState {
        val initializer = checkNotNull(episodeInitializer) { "Fight cave episode initializer is unavailable; ensure oracle content scripts are loaded." }
        return initializer.reset(player, config)
    }

    override fun resetFightCaveEpisode(player: Player, seed: Long, startWave: Int): FightCaveEpisodeState =
        resetFightCaveEpisode(player, FightCaveEpisodeConfig(seed = seed, startWave = startWave))

    override fun visibleFightCaveNpcTargets(player: Player): List<HeadlessVisibleNpcTarget> {
        val adapter = checkNotNull(actionAdapter) { "Oracle action adapter is unavailable; ensure oracle content scripts are loaded." }
        return adapter.visibleNpcTargets(player)
    }

    override fun applyFightCaveAction(player: Player, action: HeadlessAction): HeadlessActionResult {
        val adapter = checkNotNull(actionAdapter) { "Oracle action adapter is unavailable; ensure oracle content scripts are loaded." }
        return adapter.apply(player, action)
    }

    override fun applyActionsBatch(players: List<Player>, actions: List<HeadlessAction>): List<HeadlessActionResult> {
        require(players.size == actions.size) {
            "Batch action application requires player/action parity: ${players.size} != ${actions.size}."
        }
        return players.zip(actions).map { (player, action) ->
            applyFightCaveAction(player, action)
        }
    }

    override fun observeFightCave(player: Player, includeFutureLeakage: Boolean): HeadlessObservationV1 {
        val builder = checkNotNull(observationBuilder) { "Oracle observation builder is unavailable; ensure oracle content scripts are loaded." }
        return builder.build(player, includeFutureLeakage)
    }

    override fun observeFightCaveFlat(player: Player): HeadlessTrainingFlatObservationV1 {
        val builder = checkNotNull(flatObservationBuilder) { "Oracle flat observation builder is unavailable; ensure oracle content scripts are loaded." }
        return builder.build(player)
    }

    override fun observeFlatBatch(players: List<Player>): HeadlessTrainingFlatObservationBatchV1 =
        packFlatObservationBatch(players.map(::observeFightCaveFlat))

    override fun shutdown() {
        World.clear()
        org.koin.core.context.stopKoin()
        Settings.clear()
    }
}

object OracleMain {
    private val logger = InlineLogger()

    fun bootstrap(
        loadContentScripts: Boolean = true,
        startWorld: Boolean = true,
        installShutdownHook: Boolean = true,
        settingsOverrides: Map<String, String> = emptyMap(),
    ): OracleRuntime {
        AuditLog.info("startup")
        val startTime = System.currentTimeMillis()
        val settings = loadRuntimeSettings(RuntimeMode.Headed, settingsOverrides)
        val configFiles = configFiles()

        val cache = sim.engine.timed("cache") { Cache.load(settings) }
        val scripts =
            preloadRuntime(
                cache = cache,
                configFiles = configFiles,
                runtimeMode = RuntimeMode.Headed,
                loadContentScripts = loadContentScripts,
                installShutdownHook = installShutdownHook,
                scriptAllowlist = null,
            )

        if (startWorld) {
            World.start(configFiles)
        }

        val stages = getTickStages()
        val loadedScriptClasses = scripts.map { it::class.qualifiedName ?: it::class.java.name }.toSet()
        val fightCaveScript = scripts.filterIsInstance<TzhaarFightCave>().singleOrNull()
        if (loadContentScripts) {
            checkNotNull(fightCaveScript) { "Oracle runtime did not load TzhaarFightCave script required for parity initialization." }
        }

        val actionAdapter = if (loadContentScripts) HeadlessActionAdapter(scripts) else null
        val runtime =
            OracleRuntime(
                settings = settings,
                configFiles = configFiles,
                loadedScriptClasses = loadedScriptClasses,
                loadedScripts = scripts,
                stages = stages,
                gameLoop = GameLoop(stages),
                episodeInitializer = fightCaveScript?.let { FightCaveEpisodeInitializer(it, configFiles) },
                actionAdapter = actionAdapter,
                observationBuilder = actionAdapter?.let(::HeadlessObservationBuilder),
                flatObservationBuilder = actionAdapter?.let(::HeadlessFlatObservationBuilder),
            )

        logger.info {
            "${Settings["server.name"]} loaded in ${System.currentTimeMillis() - startTime}ms (mode=${currentRuntimeMode()}, oracle_stages=${stages.size}, scripts=${loadedScriptClasses.size})"
        }
        return runtime
    }
}
