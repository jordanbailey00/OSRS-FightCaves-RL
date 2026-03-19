import com.github.michaelbull.logging.InlineLogger
import content.area.karamja.tzhaar_city.TzhaarFightCave
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import sim.cache.Cache
import sim.engine.Contexts
import sim.engine.GameLoop
import sim.engine.Script
import sim.engine.data.ConfigFiles
import sim.engine.data.Settings
import sim.engine.entity.World
import sim.engine.entity.character.player.Player
import sim.engine.event.AuditLog
import java.util.*

data class HeadlessRuntime(
    val settings: Properties,
    val configFiles: ConfigFiles,
    val allowlist: HeadlessDataAllowlist,
    val scriptAllowlist: HeadlessScriptAllowlist,
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
        require(times >= 0) { "times must be >= 0, got $times." }
        var remaining = times
        while (remaining > 0) {
            GameLoop.tick++
            gameLoop.tick()
            remaining--
        }
    }

    override fun resetFightCaveEpisode(player: Player, config: FightCaveEpisodeConfig): FightCaveEpisodeState {
        val initializer = checkNotNull(episodeInitializer) { "Fight cave episode initializer is unavailable; ensure headless content scripts are loaded." }
        return initializer.reset(player, config)
    }

    override fun resetFightCaveEpisode(player: Player, seed: Long, startWave: Int): FightCaveEpisodeState =
        resetFightCaveEpisode(player, FightCaveEpisodeConfig(seed = seed, startWave = startWave))

    override fun visibleFightCaveNpcTargets(player: Player): List<HeadlessVisibleNpcTarget> {
        val adapter = checkNotNull(actionAdapter) { "Headless action adapter is unavailable; ensure headless content scripts are loaded." }
        return adapter.visibleNpcTargets(player)
    }

    override fun applyFightCaveAction(player: Player, action: HeadlessAction): HeadlessActionResult {
        val adapter = checkNotNull(actionAdapter) { "Headless action adapter is unavailable; ensure headless content scripts are loaded." }
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

    fun applyFightCaveActionAndTick(player: Player, action: HeadlessAction, ticksAfter: Int = 1): HeadlessActionResult {
        val result = applyFightCaveAction(player, action)
        tick(ticksAfter)
        return result
    }

    override fun observeFightCave(player: Player, includeFutureLeakage: Boolean): HeadlessObservationV1 {
        val builder = checkNotNull(observationBuilder) { "Headless observation builder is unavailable; ensure headless content scripts are loaded." }
        return builder.build(player, includeFutureLeakage)
    }

    override fun observeFightCaveFlat(player: Player): HeadlessTrainingFlatObservationV1 {
        val builder = checkNotNull(flatObservationBuilder) { "Headless flat observation builder is unavailable; ensure headless content scripts are loaded." }
        return builder.build(player)
    }

    override fun observeFlatBatch(players: List<Player>): HeadlessTrainingFlatObservationBatchV1 =
        packFlatObservationBatch(players.map(::observeFightCaveFlat))

    override fun shutdown() {
        World.clear()
        stopKoin()
        Settings.clear()
    }
}

object HeadlessMain {
    private val logger = InlineLogger()

    private val excludedHeadlessStages =
        setOf(
            "ConnectionQueue",
            "SaveQueue",
            "SaveLogs",
            "BotManager",
            "Hunting",
            "GrandExchange",
            "FloorItems",
        )

    private val excludedScriptPrefixes =
        listOf(
            "content.social.",
            "content.activity.",
            "content.minigame.",
        )

    private val excludedScriptSingleNamespacePrefixes =
        listOf(
            "content.quest.",
        )

    private val excludedScriptExceptions =
        setOf(
            "content.quest.Cutscene",
        )

    private val requiredPrunedSettings =
        mapOf(
            "bots.count" to "0",
            "events.shootingStars.enabled" to "false",
            "events.penguinHideAndSeek.enabled" to "false",
            "storage.autoSave.minutes" to "0",
            "spawns.npcs" to "tzhaar_city.npc-spawns.toml",
            "spawns.items" to "tzhaar_city.items.toml",
            "spawns.objects" to "tzhaar_city.objs.toml",
        )

    fun bootstrap(
        loadContentScripts: Boolean = true,
        startWorld: Boolean = true,
        installShutdownHook: Boolean = true,
        settingsOverrides: Map<String, String> = emptyMap(),
    ): HeadlessRuntime {
        AuditLog.info("startup")
        val startTime = System.currentTimeMillis()
        val settings = loadRuntimeSettings(RuntimeMode.Headless, settingsOverrides)
        val allowlist = loadHeadlessDataAllowlist()
        val configFiles = loadHeadlessConfigFiles(allowlist)
        val scriptAllowlist = loadHeadlessScriptAllowlist()

        val cache = sim.engine.timed("cache") { Cache.load(settings) }
        val scripts =
            preloadRuntime(
                cache = cache,
                configFiles = configFiles,
                runtimeMode = RuntimeMode.Headless,
                extraModules = listOf(HeadlessModules.module()),
                loadContentScripts = loadContentScripts,
                installShutdownHook = installShutdownHook,
                scriptAllowlist = if (loadContentScripts) scriptAllowlist.classNames.toSet() else null,
            )

        if (loadContentScripts) {
            validateHeadlessScriptRuntime(scriptAllowlist, scripts)
        }

        if (startWorld) {
            World.start(configFiles)
        }

        val stages = getHeadlessTickStages()
        val loadedScriptClasses = scripts.map { it::class.qualifiedName ?: it::class.java.name }.toSet()
        validateHeadlessPruningGuards(stages, loadedScriptClasses)

        val fightCaveScript = scripts.filterIsInstance<TzhaarFightCave>().singleOrNull()
        if (loadContentScripts) {
            checkNotNull(fightCaveScript) { "Headless runtime did not load TzhaarFightCave script required for episode initialization." }
        }

        val actionAdapter = if (loadContentScripts) HeadlessActionAdapter(scripts) else null
        val runtime =
            HeadlessRuntime(
                settings = settings,
                configFiles = configFiles,
                allowlist = allowlist,
                scriptAllowlist = scriptAllowlist,
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
            "${Settings["server.name"]} loaded in ${System.currentTimeMillis() - startTime}ms (mode=${currentRuntimeMode()}, headless_stages=${stages.size}, data_allowlist='${allowlist.sourcePath}', scripts=${loadedScriptClasses.size}, script_allowlist='${scriptAllowlist.sourcePath}')"
        }
        return runtime
    }

    private fun validateHeadlessPruningGuards(stages: List<Runnable>, loadedScriptClasses: Set<String>) {
        if (!Settings["headless.pruning.strict", true]) {
            return
        }

        val stageNames = describeTickStages(stages)
        val activeExcludedStages = stageNames.toSet().intersect(excludedHeadlessStages)
        check(activeExcludedStages.isEmpty()) {
            "Headless pruning invariant failed: excluded stages are active: ${activeExcludedStages.sorted()}."
        }

        val activeExcludedScripts =
            loadedScriptClasses
                .filter { className ->
                    excludedScriptPrefixes.any { prefix -> className.startsWith(prefix) } ||
                        (excludedScriptSingleNamespacePrefixes.any { prefix -> className.startsWith(prefix) } &&
                            className !in excludedScriptExceptions)
                }.sorted()
        check(activeExcludedScripts.isEmpty()) {
            "Headless pruning invariant failed: excluded script namespaces are loaded: $activeExcludedScripts"
        }

        val mismatchedSettings =
            requiredPrunedSettings.filter { (key, expectedValue) ->
                Settings[key, ""] != expectedValue
            }
        check(mismatchedSettings.isEmpty()) {
            "Headless pruning invariant failed: required pruned settings do not match expected values: $mismatchedSettings"
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val runtime = bootstrap()
        val scope = CoroutineScope(Contexts.Game)
        val engine = runtime.gameLoop.start(scope)
        AuditLog.info("game online")
        runBlocking {
            try {
                engine.join()
            } finally {
                engine.cancel()
                runtime.shutdown()
                AuditLog.info("game offline")
            }
        }
    }
}
