import content.area.karamja.tzhaar_city.JadTelegraphTrace
import content.area.karamja.tzhaar_city.JAD_HIT_CLIENT_DELAY
import content.area.karamja.tzhaar_city.JAD_HIT_RESOLVE_OFFSET_TICKS
import content.area.karamja.tzhaar_city.JAD_HIT_TARGET_QUEUE_TICKS
import content.area.karamja.tzhaar_city.beginJadTelegraphForAttack
import content.area.karamja.tzhaar_city.jadAttackSequence
import content.area.karamja.tzhaar_city.jadTelegraphTraceOrNull
import content.area.karamja.tzhaar_city.recordJadAttackOutcome
import content.entity.combat.hit.directHit
import content.entity.combat.hit.hit
import content.quest.instance
import headless.fast.FastActionCodec
import headless.fast.FastDecodedAction
import headless.fast.FastParityTrace
import headless.fast.FastProtectionPrayer
import headless.fast.FastRejectionCode
import headless.fast.FastTerminalStateEvaluator
import org.koin.core.context.stopKoin
import sim.engine.GameLoop
import sim.engine.Script
import sim.engine.client.variable.hasClock
import sim.engine.data.AccountManager
import sim.engine.data.Settings
import sim.engine.entity.World
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.item.floor.FloorItems
import sim.engine.entity.obj.GameObjects
import sim.engine.get
import sim.engine.map.collision.Collisions
import sim.engine.map.instance.Instances
import sim.engine.queue.strongQueue
import sim.types.RandomDiagnostics
import sim.types.Tile
import sim.types.randomCallCount
import sim.types.randomDiagnostics
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class ParityRuntimePath(val id: String) {
    Oracle("oracle"),
    Headless("headless"),
}

data class ParityScenarioSpec(
    val scenarioId: String,
    val seed: Long,
    val startWave: Int,
    val tickCap: Int,
    val playerName: String,
    val actionTrace: List<HeadlessReplayStep>,
    val settingsOverrides: Map<String, String>,
    val configurePlayer: (Player) -> Unit = {},
    val stepHook: ParityStepHook = ParityStepHook { _, _, _, _ -> },
)

data class ParityCombatState(
    val actionDelay: Boolean,
    val foodDelay: Boolean,
    val drinkDelay: Boolean,
    val comboDelay: Boolean,
    val stunned: Boolean,
    val delayed: Boolean,
    val playerHitsplatCount: Int,
    val visibleNpcHitsplatCounts: List<Int>,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "action_delay" to actionDelay,
            "food_delay" to foodDelay,
            "drink_delay" to drinkDelay,
            "combo_delay" to comboDelay,
            "stunned" to stunned,
            "delay" to delayed,
            "player_hitsplat_count" to playerHitsplatCount,
            "visible_npc_hitsplat_counts" to visibleNpcHitsplatCounts,
        )
}

data class ParityNpcState(
    val id: String,
    val tile: HeadlessObservationTile,
    val hitpointsCurrent: Int,
    val hitpointsMax: Int,
    val hidden: Boolean,
    val dead: Boolean,
    val underAttack: Boolean,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "id" to id,
            "tile" to tile.toOrderedMap(),
            "hitpoints_current" to hitpointsCurrent,
            "hitpoints_max" to hitpointsMax,
            "hidden" to hidden,
            "dead" to dead,
            "under_attack" to underAttack,
        )
}

data class ParitySnapshot(
    val stepIndex: Int,
    val tick: Int,
    val action: HeadlessAction?,
    val actionResult: HeadlessActionResult?,
    val observation: HeadlessObservationV1,
    val jadTelegraph: JadTelegraphTrace?,
    val fightCaveNpcs: List<ParityNpcState>,
    val combatState: ParityCombatState,
    val rngCallCount: Long,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any?> {
        val coreObservation = observation.toOrderedMap().toMutableMap()
        coreObservation.remove("npcs")

        return linkedMapOf(
            "step_index" to stepIndex,
            "tick" to tick,
            "action" to action?.toString(),
            "action_result" to
                linkedMapOf(
                    "action_type" to actionResult?.actionType?.name,
                    "action_id" to actionResult?.actionId,
                    "action_applied" to actionResult?.actionApplied,
                    "rejection_reason" to actionResult?.rejectionReason?.name,
                    "metadata" to (actionResult?.metadata?.filterKeys { it != "target_npc_index" }?.toSortedMap() ?: sortedMapOf<String, String>()),
                ),
            "observation" to LinkedHashMap(coreObservation),
            "jad_telegraph" to jadTelegraph?.toOrderedMap(),
            "fight_cave_npcs" to fightCaveNpcs.map { it.toOrderedMap() },
            "combat_state" to combatState.toOrderedMap(),
        )
    }
}

data class ParityRunOutput(
    val path: ParityRuntimePath,
    val snapshots: List<ParitySnapshot>,
    val rngDiagnostics: RandomDiagnostics,
)

data class ParityMismatch(
    val snapshotIndex: Int,
    val stepIndex: Int,
    val tick: Int,
    val fieldPath: String,
    val oracleValue: Any?,
    val headlessValue: Any?,
    val lastAction: String?,
    val oracleRngCallCount: Long,
    val headlessRngCallCount: Long,
)

data class ParityComparisonResult(
    val seed: Long,
    val startWave: Int,
    val actionTraceSize: Int,
    val oracle: ParityRunOutput,
    val headless: ParityRunOutput,
    val mismatch: ParityMismatch?,
    val firstDivergenceArtifact: Path?,
) {
    val passed: Boolean
        get() = mismatch == null

    fun requirePassed(): ParityComparisonResult {
        check(passed) {
            val mismatch = checkNotNull(mismatch)
            "Parity mismatch at snapshot=${mismatch.snapshotIndex} step=${mismatch.stepIndex} tick=${mismatch.tick} field=${mismatch.fieldPath} oracle='${mismatch.oracleValue}' headless='${mismatch.headlessValue}' artifact=${firstDivergenceArtifact ?: "<none>"}"
        }
        return this
    }
}

fun interface ParityStepHook {
    fun apply(path: ParityRuntimePath, stepIndex: Int, step: HeadlessReplayStep?, player: Player)
}

class ParityHarness(
    private val artifactRoot: Path = locateRepositoryRoot().resolve("temp/parity"),
) {

    fun collectMechanicsParityTrace(
        path: ParityRuntimePath,
        seed: Long,
        actionTrace: List<HeadlessReplayStep>,
        startWave: Int = 1,
        tickCap: Int = 20_000,
        playerName: String = "parity-mechanics-trace",
        settingsOverrides: Map<String, String> = emptyMap(),
        configurePlayer: (Player) -> Unit = {},
        stepHook: ParityStepHook = ParityStepHook { _, _, _, _ -> },
    ): List<FastParityTrace> {
        val run =
            runPath(
                path = path,
                seed = seed,
                actionTrace = actionTrace,
                startWave = startWave,
                includeFutureLeakage = false,
                playerName = playerName,
                settingsOverrides = settingsOverrides,
                configurePlayer = configurePlayer,
                stepHook = stepHook,
            )
        return exportMechanicsParityTrace(run.snapshots, tickCap = tickCap)
    }

    fun collectMechanicsParityTrace(
        pathId: String,
        seed: Long,
        packedActions: IntArray,
        startWave: Int = 1,
        tickCap: Int = 20_000,
        playerName: String = "parity-mechanics-trace",
        settingsOverrides: Map<String, String> = emptyMap(),
    ): List<FastParityTrace> =
        collectMechanicsParityTrace(
            path = resolveRuntimePath(pathId),
            seed = seed,
            actionTrace = decodePackedActionTrace(packedActions),
            startWave = startWave,
            tickCap = tickCap,
            playerName = playerName,
            settingsOverrides = settingsOverrides,
        )

    fun collectScenarioMechanicsParityTrace(
        pathId: String,
        scenarioId: String,
        settingsOverrides: Map<String, String>,
    ): List<FastParityTrace> {
        val scenario = resolveParityScenario(scenarioId)
        return collectMechanicsParityTrace(
            path = resolveRuntimePath(pathId),
            seed = scenario.seed,
            actionTrace = scenario.actionTrace,
            startWave = scenario.startWave,
            tickCap = scenario.tickCap,
            playerName = scenario.playerName,
            settingsOverrides = scenario.settingsOverrides + settingsOverrides,
            configurePlayer = scenario.configurePlayer,
            stepHook = scenario.stepHook,
        )
    }

    fun collectScenarioMechanicsParityTrace(
        pathId: String,
        scenarioId: String,
    ): List<FastParityTrace> =
        collectScenarioMechanicsParityTrace(
            pathId = pathId,
            scenarioId = scenarioId,
            settingsOverrides = emptyMap(),
        )

    fun runAndCompare(
        seed: Long,
        actionTrace: List<HeadlessReplayStep>,
        startWave: Int = 1,
        includeFutureLeakage: Boolean = false,
        playerName: String = "parity-player",
        settingsOverrides: Map<String, String> = emptyMap(),
        configurePlayer: (Player) -> Unit = {},
        stepHook: ParityStepHook = ParityStepHook { _, _, _, _ -> },
    ): ParityComparisonResult {
        require(startWave in 1..63) { "Fight Caves start wave must be in range 1..63, got $startWave." }

        val oracleRun =
            runPath(
                path = ParityRuntimePath.Oracle,
                seed = seed,
                actionTrace = actionTrace,
                startWave = startWave,
                includeFutureLeakage = includeFutureLeakage,
                playerName = "$playerName-oracle",
                settingsOverrides = settingsOverrides,
                configurePlayer = configurePlayer,
                stepHook = stepHook,
            )

        val headlessRun =
            runPath(
                path = ParityRuntimePath.Headless,
                seed = seed,
                actionTrace = actionTrace,
                startWave = startWave,
                includeFutureLeakage = includeFutureLeakage,
                playerName = "$playerName-headless",
                settingsOverrides = settingsOverrides,
                configurePlayer = configurePlayer,
                stepHook = stepHook,
            )

        val mismatch = firstMismatch(oracleRun, headlessRun)
        val artifact = mismatch?.let { persistFirstDivergence(it, seed, startWave, actionTrace, oracleRun, headlessRun) }

        return ParityComparisonResult(
            seed = seed,
            startWave = startWave,
            actionTraceSize = actionTrace.size,
            oracle = oracleRun,
            headless = headlessRun,
            mismatch = mismatch,
            firstDivergenceArtifact = artifact,
        )
    }

    private fun runPath(
        path: ParityRuntimePath,
        seed: Long,
        actionTrace: List<HeadlessReplayStep>,
        startWave: Int,
        includeFutureLeakage: Boolean,
        playerName: String,
        settingsOverrides: Map<String, String>,
        configurePlayer: (Player) -> Unit,
        stepHook: ParityStepHook,
    ): ParityRunOutput {
        resetRuntimeState()
        val runtime = bootstrap(path, settingsOverrides)
        try {
            val player = createPlayer("$playerName-${path.id}-${System.nanoTime()}")
            configurePlayer(player)
            runtime.resetFightCaveEpisode(player, seed = seed, startWave = startWave)

            val snapshots = mutableListOf<ParitySnapshot>()
            stepHook.apply(path, -1, null, player)
            snapshots += snapshot(runtime, player, includeFutureLeakage, stepIndex = -1, action = null, actionResult = null)

            for ((index, step) in actionTrace.withIndex()) {
                val actionResult = runtime.applyFightCaveAction(player, step.action)
                stepHook.apply(path, index, step, player)
                if (step.ticksAfter > 0) {
                    runtime.tick(step.ticksAfter)
                }
                snapshots += snapshot(runtime, player, includeFutureLeakage, stepIndex = index, action = step.action, actionResult = actionResult)
            }

            return ParityRunOutput(
                path = path,
                snapshots = snapshots,
                rngDiagnostics = randomDiagnostics(),
            )
        } finally {
            runtime.shutdown()
            resetRuntimeState()
        }
    }

    private fun bootstrap(path: ParityRuntimePath, settingsOverrides: Map<String, String>): FightCaveSimulationRuntime =
        when (path) {
            ParityRuntimePath.Oracle ->
                OracleMain.bootstrap(
                    loadContentScripts = true,
                    startWorld = false,
                    installShutdownHook = false,
                    settingsOverrides = settingsOverrides,
                )

            ParityRuntimePath.Headless ->
                HeadlessMain.bootstrap(
                    loadContentScripts = true,
                    startWorld = false,
                    installShutdownHook = false,
                    settingsOverrides = settingsOverrides,
                )
        }

    private fun createPlayer(name: String, tile: Tile = Tile(2438, 5168)): Player {
        val accounts: AccountManager = get()
        val player = Player(tile = tile, accountName = name, passwordHash = "")
        check(accounts.setup(player, null, 0, viewport = true)) { "Failed to setup parity player '$name'." }
        player["creation"] = -1
        player["skip_level_up"] = true
        player["fight_cave_wave"] = -1
        player["fight_caves_logout_warning"] = false
        player["logged_out"] = false
        accounts.spawn(player, null)
        player.queue.clear()
        player.softTimers.stopAll()
        player.timers.stopAll()
        player.clear("fight_cave_wave")
        player.clear("fight_cave_rotation")
        player.clear("fight_cave_remaining")
        player["fight_caves_logout_warning"] = false
        player["logged_out"] = false
        player.viewport?.loaded = true
        return player
    }

    private fun snapshot(
        runtime: FightCaveSimulationRuntime,
        player: Player,
        includeFutureLeakage: Boolean,
        stepIndex: Int,
        action: HeadlessAction?,
        actionResult: HeadlessActionResult?,
    ): ParitySnapshot {
        val observation = runtime.observeFightCave(player, includeFutureLeakage = includeFutureLeakage)

        val npcHitSplats =
            runtime
                .visibleFightCaveNpcTargets(player)
                .map { target ->
                    val npc = NPCs.indexed(target.npcIndex)
                    npc?.visuals?.hits?.splats?.count { it != null } ?: 0
                }

        val allFightCaveNpcs =
            fightCaveNpcs(player)
                .sortedWith(compareBy<NPC>({ it.tile.level }, { it.tile.x }, { it.tile.y }, { it.id }))
        val jadTelegraph = allFightCaveNpcs.firstNotNullOfOrNull { it.jadTelegraphTraceOrNull() }
        val allFightCaveNpcStates =
            allFightCaveNpcs.map { npc ->
                    val hp = npc.levels.get(Skill.Constitution)
                    val max = npc.levels.getMax(Skill.Constitution)
                    ParityNpcState(
                        id = npc.id,
                        tile = HeadlessObservationTile.from(npc.tile),
                        hitpointsCurrent = hp,
                        hitpointsMax = max,
                        hidden = npc.hide,
                        dead = npc.contains("dead") || hp <= 0,
                        underAttack = npc.hasClock("under_attack"),
                    )
                }

        return ParitySnapshot(
            stepIndex = stepIndex,
            tick = observation.tick,
            action = action,
            actionResult = actionResult,
            observation = observation,
            jadTelegraph = jadTelegraph,
            fightCaveNpcs = allFightCaveNpcStates,
            combatState =
                ParityCombatState(
                    actionDelay = player.hasClock("action_delay"),
                    foodDelay = player.hasClock("food_delay"),
                    drinkDelay = player.hasClock("drink_delay"),
                    comboDelay = player.hasClock("combo_delay"),
                    stunned = player.hasClock("stunned"),
                    delayed = player.contains("delay"),
                    playerHitsplatCount = player.visuals.hits.splats.count { it != null },
                    visibleNpcHitsplatCounts = npcHitSplats,
                ),
            rngCallCount = randomCallCount(),
        )
    }

    private fun exportMechanicsParityTrace(
        snapshots: List<ParitySnapshot>,
        tickCap: Int,
    ): List<FastParityTrace> {
        if (snapshots.isEmpty()) {
            return emptyList()
        }
        val episodeStartTick = snapshots.first().tick
        return snapshots.mapIndexed { index, snapshot ->
            val previous = snapshots.getOrNull(index - 1)
            val observation = snapshot.observation
            val jadTrace = snapshot.jadTelegraph
            val terminal =
                FastTerminalStateEvaluator.infer(
                    playerHitpointsCurrent = observation.player.hitpointsCurrent,
                    waveId = observation.wave.wave,
                    remainingNpcs = observation.wave.remaining,
                    observationTick = observation.tick,
                    episodeStartTick = episodeStartTick,
                    tickCap = tickCap,
                )
            FastParityTrace(
                tickIndex = observation.tick - episodeStartTick,
                actionName = parityActionName(snapshot.action),
                actionAccepted = snapshot.actionResult?.actionApplied ?: true,
                rejectionCode = parityRejectionCode(snapshot.actionResult?.rejectionReason).code,
                playerHitpoints = observation.player.hitpointsCurrent,
                playerPrayerPoints = observation.player.prayerCurrent,
                runEnabled = observation.player.running,
                inventoryAmmo = observation.player.consumables.ammoCount,
                inventorySharks = observation.player.consumables.sharkCount,
                inventoryPrayerPotions = observation.player.consumables.prayerPotionDoseCount,
                waveId = observation.wave.wave,
                remainingNpcs = observation.wave.remaining,
                visibleTargetOrder = observation.npcs.map { it.npcIndex }.toIntArray(),
                visibleNpcType = observation.npcs.map { it.id }.toTypedArray(),
                visibleNpcHitpoints = observation.npcs.map { it.hitpointsCurrent }.toIntArray(),
                visibleNpcAlive =
                    observation.npcs.map { npc -> !npc.dead && npc.hitpointsCurrent > 0 }.toBooleanArray(),
                jadTelegraphState = jadTrace?.telegraphStateCode ?: 0,
                jadHitResolveOutcome = jadResolveOutcome(jadTrace),
                damageDealt = parityDamageDealt(previous, snapshot),
                damageTaken = parityDamageTaken(previous, snapshot),
                terminalCode = terminal.terminalCode,
            )
        }
    }

    private fun fightCaveNpcs(player: Player): List<NPC> {
        val instance = player.instance()
        val source =
            if (instance != null) {
                buildList {
                    for (level in 0..3) {
                        addAll(NPCs.at(instance.toLevel(level)))
                    }
                }
            } else {
                NPCs.at(player.tile.regionLevel)
            }

        return source.filter { it.id in FIGHT_CAVE_NPC_IDS }
    }

    private fun resolveRuntimePath(pathId: String): ParityRuntimePath =
        ParityRuntimePath.values().firstOrNull { it.id == pathId }
            ?: error("Unsupported parity runtime path '$pathId'.")

    private fun decodePackedActionTrace(packedActions: IntArray): List<HeadlessReplayStep> {
        require(packedActions.size % FastActionCodec.PACKED_WORD_COUNT == 0) {
            "Packed parity action trace length must be a multiple of ${FastActionCodec.PACKED_WORD_COUNT}, " +
                "got ${packedActions.size}."
        }
        val actionCount = packedActions.size / FastActionCodec.PACKED_WORD_COUNT
        return List(actionCount) { actionIndex ->
            val decoded =
                FastActionCodec.decode(
                    packedActions = packedActions,
                    offset = actionIndex * FastActionCodec.PACKED_WORD_COUNT,
                )
            HeadlessReplayStep(action = toHeadlessAction(decoded), ticksAfter = 1)
        }
    }

    private fun toHeadlessAction(decoded: FastDecodedAction): HeadlessAction =
        when (decoded.actionId) {
            0 -> HeadlessAction.Wait
            1 -> {
                val tile = checkNotNull(decoded.tile)
                HeadlessAction.WalkToTile(Tile(tile.x, tile.y, tile.level))
            }
            2 -> HeadlessAction.AttackVisibleNpc(checkNotNull(decoded.visibleNpcIndex))
            3 ->
                HeadlessAction.ToggleProtectionPrayer(
                    when (checkNotNull(decoded.prayer)) {
                        FastProtectionPrayer.ProtectFromMagic -> HeadlessProtectionPrayer.ProtectFromMagic
                        FastProtectionPrayer.ProtectFromMissiles -> HeadlessProtectionPrayer.ProtectFromMissiles
                        FastProtectionPrayer.ProtectFromMelee -> HeadlessProtectionPrayer.ProtectFromMelee
                        FastProtectionPrayer.None -> error("None is not a valid toggle prayer action.")
                    },
                )
            4 -> HeadlessAction.EatShark
            5 -> HeadlessAction.DrinkPrayerPotion
            6 -> HeadlessAction.ToggleRun
            else -> HeadlessAction.Wait
        }

    private fun parityActionName(action: HeadlessAction?): String =
        when (action?.type) {
            null -> "reset"
            HeadlessActionType.Wait -> "wait"
            HeadlessActionType.WalkToTile -> "walk_to_tile"
            HeadlessActionType.AttackVisibleNpc -> "attack_visible_npc"
            HeadlessActionType.ToggleProtectionPrayer -> "toggle_protection_prayer"
            HeadlessActionType.EatShark -> "eat_shark"
            HeadlessActionType.DrinkPrayerPotion -> "drink_prayer_potion"
            HeadlessActionType.ToggleRun -> "toggle_run"
        }

    private fun parityRejectionCode(reason: HeadlessActionRejectReason?): FastRejectionCode =
        when (reason) {
            null -> FastRejectionCode.None
            HeadlessActionRejectReason.AlreadyActedThisTick -> FastRejectionCode.AlreadyActedThisTick
            HeadlessActionRejectReason.InvalidTargetIndex -> FastRejectionCode.InvalidTargetIndex
            HeadlessActionRejectReason.TargetNotVisible -> FastRejectionCode.TargetNotVisible
            HeadlessActionRejectReason.PlayerBusy -> FastRejectionCode.PlayerBusy
            HeadlessActionRejectReason.MissingConsumable -> FastRejectionCode.MissingConsumable
            HeadlessActionRejectReason.ConsumptionLocked -> FastRejectionCode.ConsumptionLocked
            HeadlessActionRejectReason.PrayerPointsDepleted -> FastRejectionCode.PrayerPointsDepleted
            HeadlessActionRejectReason.InsufficientRunEnergy -> FastRejectionCode.InsufficientRunEnergy
            HeadlessActionRejectReason.NoMovementRequired -> FastRejectionCode.NoMovementRequired
        }

    private fun parityDamageDealt(previous: ParitySnapshot?, current: ParitySnapshot): Float {
        if (previous == null) {
            return 0f
        }
        val before = previous.fightCaveNpcs.sumOf { it.hitpointsCurrent.coerceAtLeast(0) }
        val after = current.fightCaveNpcs.sumOf { it.hitpointsCurrent.coerceAtLeast(0) }
        return (before - after).coerceAtLeast(0).toFloat()
    }

    private fun parityDamageTaken(previous: ParitySnapshot?, current: ParitySnapshot): Float {
        if (previous == null) {
            return 0f
        }
        return (
            previous.observation.player.hitpointsCurrent -
                current.observation.player.hitpointsCurrent
        ).coerceAtLeast(0).toFloat()
    }

    private fun jadResolveOutcome(trace: JadTelegraphTrace?): String =
        when {
            trace == null -> "none"
            trace.resolvedDamage < 0 -> "pending"
            trace.protectedAtPrayerCheck -> "protected"
            trace.resolvedDamage == 0 -> "zero_damage"
            else -> "hit"
        }

    private fun firstMismatch(oracleRun: ParityRunOutput, headlessRun: ParityRunOutput): ParityMismatch? {
        if (oracleRun.snapshots.size != headlessRun.snapshots.size) {
            return ParityMismatch(
                snapshotIndex = -1,
                stepIndex = -1,
                tick = -1,
                fieldPath = "snapshots.size",
                oracleValue = oracleRun.snapshots.size,
                headlessValue = headlessRun.snapshots.size,
                lastAction = null,
                oracleRngCallCount = oracleRun.rngDiagnostics.callCount,
                headlessRngCallCount = headlessRun.rngDiagnostics.callCount,
            )
        }

        for (index in oracleRun.snapshots.indices) {
            val oracle = oracleRun.snapshots[index]
            val headless = headlessRun.snapshots[index]
            val difference = firstDifference(oracle.toOrderedMap(), headless.toOrderedMap(), "snapshot[$index]") ?: continue
            return ParityMismatch(
                snapshotIndex = index,
                stepIndex = oracle.stepIndex,
                tick = oracle.tick,
                fieldPath = difference.path,
                oracleValue = difference.oracleValue,
                headlessValue = difference.headlessValue,
                lastAction = oracle.action?.toString(),
                oracleRngCallCount = oracle.rngCallCount,
                headlessRngCallCount = headless.rngCallCount,
            )
        }

        return null
    }

    private fun firstDifference(oracle: Any?, headless: Any?, path: String): Difference? {
        if (oracle is Map<*, *> && headless is Map<*, *>) {
            val oracleKeys = oracle.keys.map { it.toString() }
            val headlessKeys = headless.keys.map { it.toString() }
            if (oracleKeys != headlessKeys) {
                return Difference("$path.<keys>", oracleKeys, headlessKeys)
            }
            for (key in oracleKeys) {
                val difference = firstDifference(oracle[key], headless[key], "$path.$key")
                if (difference != null) {
                    return difference
                }
            }
            return null
        }

        if (oracle is List<*> && headless is List<*>) {
            if (oracle.size != headless.size) {
                return Difference("$path.size", oracle.size, headless.size)
            }
            for (index in oracle.indices) {
                val difference = firstDifference(oracle[index], headless[index], "$path[$index]")
                if (difference != null) {
                    return difference
                }
            }
            return null
        }

        if (oracle != headless) {
            return Difference(path, oracle, headless)
        }

        return null
    }

    private fun persistFirstDivergence(
        mismatch: ParityMismatch,
        seed: Long,
        startWave: Int,
        actionTrace: List<HeadlessReplayStep>,
        oracleRun: ParityRunOutput,
        headlessRun: ParityRunOutput,
    ): Path {
        val timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS'Z'"))
        val directory = artifactRoot.resolve("first_divergence")
        Files.createDirectories(directory)
        val path = directory.resolve("parity_seed_${seed}_wave_${startWave}_$timestamp.txt")

        val oracleSnapshot = oracleRun.snapshots.getOrNull(mismatch.snapshotIndex)
        val headlessSnapshot = headlessRun.snapshots.getOrNull(mismatch.snapshotIndex)
        val payload =
            buildString {
                appendLine("seed=$seed")
                appendLine("start_wave=$startWave")
                appendLine("action_trace_size=${actionTrace.size}")
                appendLine("snapshot_index=${mismatch.snapshotIndex}")
                appendLine("step_index=${mismatch.stepIndex}")
                appendLine("tick=${mismatch.tick}")
                appendLine("field_path=${mismatch.fieldPath}")
                appendLine("last_action=${mismatch.lastAction ?: "<none>"}")
                appendLine("oracle_value=${mismatch.oracleValue}")
                appendLine("headless_value=${mismatch.headlessValue}")
                appendLine("oracle_rng_call_count=${mismatch.oracleRngCallCount}")
                appendLine("headless_rng_call_count=${mismatch.headlessRngCallCount}")
                appendLine()
                appendLine("oracle_snapshot=${oracleSnapshot?.toOrderedMap()}")
                appendLine("headless_snapshot=${headlessSnapshot?.toOrderedMap()}")
                appendLine()
                appendLine("action_trace=$actionTrace")
            }

        Files.writeString(path, payload)
        return path
    }

    private fun resetRuntimeState() {
        Script.clear()
        runCatching { Players.clear() }
        runCatching { NPCs.clear() }
        runCatching { FloorItems.clear() }
        runCatching { GameObjects.reset() }
        runCatching { World.clear() }
        runCatching { Instances.reset() }
        runCatching { Collisions.clear() }
        GameLoop.tick = 0
        Settings.clear()
        stopKoin()
    }

    private data class Difference(
        val path: String,
        val oracleValue: Any?,
        val headlessValue: Any?,
    )

    companion object {
        private val FIGHT_CAVE_NPC_IDS =
            setOf(
                "tz_kih",
                "tz_kih_spawn_point",
                "tz_kek",
                "tz_kek_spawn_point",
                "tz_kek_spawn",
                "tok_xil",
                "tok_xil_spawn_point",
                "yt_mej_kot",
                "yt_mej_kot_spawn_point",
                "ket_zek",
                "ket_zek_spawn_point",
                "tztok_jad",
                "yt_hur_kot",
            )
    }
}

private fun parityScenarioOverrides(): Map<String, String> =
    mapOf(
        "storage.data" to "../data/",
        "storage.data.modified" to "../temp/data/headless-test-cache/modified.dat",
        "storage.cache.path" to "../data/cache/",
        "storage.wildcards" to "../temp/data/headless-test-cache/wildcards.txt",
        "storage.caching.path" to "../temp/data/headless-test-cache/",
        "storage.caching.active" to "false",
        "storage.players.path" to "../temp/data/test-saves/",
        "storage.players.logs" to "../temp/data/test-logs/",
        "storage.players.errors" to "../temp/data/test-errors/",
        "headless.data.allowlist.path" to "config/headless_data_allowlist.toml",
        "headless.scripts.allowlist.path" to "config/headless_scripts.txt",
        "headless.manifest.path" to "config/headless_manifest.toml",
        "runtime.mode" to RuntimeMode.Headed.id,
        "headless.map.regions" to "",
        "storage.disabled" to "true",
        "world.npcs.randomWalk" to "false",
        "events.shootingStars.enabled" to "false",
        "events.penguinHideAndSeek.enabled" to "false",
        "storage.autoSave.minutes" to "0",
        "bots.count" to "0",
        "bots.numberedNames" to "true",
        "bots.names" to "../data/bot_names.txt",
        "spawns.npcs" to "tzhaar_city.npc-spawns.toml",
        "spawns.items" to "tzhaar_city.items.toml",
        "spawns.objects" to "tzhaar_city.objs.toml",
    )

private fun defaultParityPlayerSetup(player: Player) {
    player["auto_retaliate"] = true
    player["god_mode"] = true
}

private fun scenarioSingleWaveTrace(): List<HeadlessReplayStep> =
    listOf(
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 2),
        HeadlessReplayStep(HeadlessAction.AttackVisibleNpc(0), ticksAfter = 8),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 10),
        HeadlessReplayStep(HeadlessAction.AttackVisibleNpc(0), ticksAfter = 8),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 12),
    )

private fun scenarioFullRunTrace(steps: Int = 160): List<HeadlessReplayStep> =
    List(steps) {
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 7)
    }

private fun scenarioJadHealerTrace(): List<HeadlessReplayStep> =
    listOf(
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 5),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 3),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 4),
    )

private fun scenarioTzKekSplitTrace(): List<HeadlessReplayStep> =
    listOf(
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 5),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 7),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 2),
    )

private val scenarioFullRunWaveClearHook =
    ParityStepHook { _, stepIndex, _, player ->
        if (stepIndex < 0) {
            return@ParityStepHook
        }
        for (npc in parityScenarioFightCaveNpcs(player)) {
            npc.directHit(player, npc.levels.get(Skill.Constitution))
        }
    }

private val scenarioJadHalfHpHook =
    ParityStepHook { _, stepIndex, _, player ->
        if (stepIndex != 0) {
            return@ParityStepHook
        }
        val jad = parityScenarioFightCaveNpcs(player).firstOrNull { it.id == "tztok_jad" } ?: return@ParityStepHook
        val damage = 1 + (jad.levels.getMax(Skill.Constitution) / 2)
        jad.directHit(player, damage)
    }

private val scenarioTzKekSplitHook =
    ParityStepHook { _, stepIndex, _, player ->
        if (stepIndex < 0 || player["parity_tzkek_split_applied", false]) {
            return@ParityStepHook
        }

        val targets =
            parityScenarioFightCaveNpcs(player)
                .filter { it.id == "tz_kek" || it.id == "tz_kek_spawn_point" }
        if (targets.isEmpty()) {
            return@ParityStepHook
        }

        for (target in targets) {
            target.directHit(player, target.levels.get(Skill.Constitution))
        }
        player["parity_tzkek_split_applied"] = true
    }

private fun parityScenarioFightCaveNpcs(player: Player): List<NPC> {
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

private fun spawnScenarioJadWithTelegraphedAttack(
    player: Player,
    attackId: String,
    baseDamage: Int = 100,
): NPC {
    val jad = NPCs.add("tztok_jad", player.tile.add(1, 0))
    NPCs.run()
    check(jad.beginJadTelegraphForAttack(attackId)) { "Failed to start Jad telegraph for attack '$attackId'." }
    jad.strongQueue("jad_test_hit_target_${jad.jadAttackSequence}", JAD_HIT_TARGET_QUEUE_TICKS) {
        val resolvedDamage =
            jad.hit(
                target = player,
                offensiveType = attackId,
                delay = JAD_HIT_CLIENT_DELAY,
                damage = baseDamage,
            )
        jad.recordJadAttackOutcome(player, resolvedDamage)
    }
    return jad
}

private fun resolveParityScenario(scenarioId: String): ParityScenarioSpec =
    when (scenarioId) {
        "single_wave" ->
            ParityScenarioSpec(
                scenarioId = scenarioId,
                seed = 11_001L,
                startWave = 1,
                tickCap = 20_000,
                playerName = "parity-single-wave",
                actionTrace = scenarioSingleWaveTrace(),
                settingsOverrides = parityScenarioOverrides(),
                configurePlayer = ::defaultParityPlayerSetup,
            )
        "full_run" ->
            ParityScenarioSpec(
                scenarioId = scenarioId,
                seed = 22_002L,
                startWave = 1,
                tickCap = 20_000,
                playerName = "parity-full-run",
                actionTrace = scenarioFullRunTrace(),
                settingsOverrides = parityScenarioOverrides(),
                configurePlayer = ::defaultParityPlayerSetup,
                stepHook = scenarioFullRunWaveClearHook,
            )
        "jad_healer" ->
            ParityScenarioSpec(
                scenarioId = scenarioId,
                seed = 33_003L,
                startWave = 63,
                tickCap = 20_000,
                playerName = "parity-jad-healers",
                actionTrace = scenarioJadHealerTrace(),
                settingsOverrides = parityScenarioOverrides(),
                configurePlayer = ::defaultParityPlayerSetup,
                stepHook = scenarioJadHalfHpHook,
            )
        "tzkek_split" ->
            ParityScenarioSpec(
                scenarioId = scenarioId,
                seed = 44_004L,
                startWave = 3,
                tickCap = 20_000,
                playerName = "parity-tzkek-split",
                actionTrace = scenarioTzKekSplitTrace(),
                settingsOverrides = parityScenarioOverrides(),
                configurePlayer = ::defaultParityPlayerSetup,
                stepHook = scenarioTzKekSplitHook,
            )
        "jad_telegraph" ->
            ParityScenarioSpec(
                scenarioId = scenarioId,
                seed = 63_063L,
                startWave = 1,
                tickCap = 20_000,
                playerName = "parity-jad-telegraph",
                actionTrace = emptyList(),
                settingsOverrides = parityScenarioOverrides(),
                configurePlayer = ::defaultParityPlayerSetup,
                stepHook =
                    ParityStepHook { _, stepIndex, _, player ->
                        if (stepIndex != -1) {
                            return@ParityStepHook
                        }
                        val jad = NPCs.add("tztok_jad", player.tile.add(1, 0))
                        NPCs.run()
                        check(jad.beginJadTelegraphForAttack("range"))
                    },
            )
        "jad_prayer_protected" ->
            ParityScenarioSpec(
                scenarioId = scenarioId,
                seed = 63_071L,
                startWave = 1,
                tickCap = 20_000,
                playerName = "parity-jad-prayer-protected",
                actionTrace =
                    listOf(
                        HeadlessReplayStep(
                            HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMagic),
                            ticksAfter = JAD_HIT_TARGET_QUEUE_TICKS,
                        ),
                        HeadlessReplayStep(
                            HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMagic),
                            ticksAfter = JAD_HIT_RESOLVE_OFFSET_TICKS - JAD_HIT_TARGET_QUEUE_TICKS,
                        ),
                    ),
                settingsOverrides = parityScenarioOverrides(),
                configurePlayer = { player -> player["auto_retaliate"] = true },
                stepHook =
                    ParityStepHook { _, stepIndex, _, player ->
                        if (stepIndex == -1) {
                            NPCs.clear()
                            spawnScenarioJadWithTelegraphedAttack(player, attackId = "magic")
                        }
                    },
            )
        "jad_prayer_too_late" ->
            ParityScenarioSpec(
                scenarioId = scenarioId,
                seed = 63_072L,
                startWave = 1,
                tickCap = 20_000,
                playerName = "parity-jad-prayer-too-late",
                actionTrace =
                    listOf(
                        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = JAD_HIT_RESOLVE_OFFSET_TICKS - 1),
                        HeadlessReplayStep(
                            HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMissiles),
                            ticksAfter = 1,
                        ),
                    ),
                settingsOverrides = parityScenarioOverrides(),
                configurePlayer = { player -> player["auto_retaliate"] = true },
                stepHook =
                    ParityStepHook { _, stepIndex, _, player ->
                        if (stepIndex == -1) {
                            NPCs.clear()
                            spawnScenarioJadWithTelegraphedAttack(player, attackId = "range")
                        }
                    },
            )
        else -> error("Unsupported parity scenario '$scenarioId'.")
    }





