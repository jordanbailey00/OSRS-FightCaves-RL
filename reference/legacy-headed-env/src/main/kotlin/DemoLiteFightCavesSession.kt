import content.quest.instance
import world.gregs.voidps.engine.entity.character.mode.move.Movement
import world.gregs.voidps.engine.entity.character.npc.NPC
import world.gregs.voidps.engine.entity.character.npc.NPCs
import world.gregs.voidps.engine.client.variable.hasClock
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.type.Tile

data class DemoLiteMovementSnapshot(
    val currentTile: HeadlessObservationTile,
    val destinationTile: HeadlessObservationTile?,
    val movementState: String,
    val movementSource: String,
    val movementSourceDetail: String?,
    val mode: String,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any?> =
        linkedMapOf(
            "current_tile" to currentTile.toOrderedMap(),
            "destination_tile" to destinationTile?.toOrderedMap(),
            "movement_state" to movementState,
            "movement_source" to movementSource,
            "movement_source_detail" to movementSourceDetail,
            "mode" to mode,
        )
}

data class DemoLiteTrackedNpcSnapshot(
    val npcIndex: Int,
    val id: String,
    val tile: HeadlessObservationTile,
    val hidden: Boolean,
    val dead: Boolean,
    val underAttack: Boolean,
    val visibleToPlayer: Boolean,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "npc_index" to npcIndex,
            "id" to id,
            "tile" to tile.toOrderedMap(),
            "hidden" to hidden,
            "dead" to dead,
            "under_attack" to underAttack,
            "visible_to_player" to visibleToPlayer,
        )
}

enum class DemoLiteTerminalCode(val code: Int) {
    NONE(0),
    PLAYER_DEATH(1),
    CAVE_COMPLETE(2),
    TICK_CAP(3),
    INVALID_STATE(4),
    ORACLE_ABORT(5),
}

data class DemoLiteTickState(
    val observation: HeadlessObservationV1,
    val visibleTargets: List<HeadlessVisibleNpcTarget>,
    val trackedNpcs: List<DemoLiteTrackedNpcSnapshot>,
    val movement: DemoLiteMovementSnapshot,
    val lastAction: HeadlessAction?,
    val lastActionResult: HeadlessActionResult?,
    val terminalCode: DemoLiteTerminalCode,
    val resetCount: Int,
    val sessionSeed: Long,
    val tickCap: Int,
    val autoResetRemainingMillis: Long? = null,
)

class DemoLiteFightCavesSession(
    private val runtime: OracleRuntime,
    private val episodeInitializer: DemoLiteEpisodeInitializer,
    private val config: DemoLiteConfig,
) {
    private var episodeContext: DemoLiteEpisodeContext? = null
    private var episodeStartTick: Int = 0
    private var currentState: DemoLiteTickState? = null

    fun reset(): DemoLiteTickState {
        val nextEpisode = episodeInitializer.resetWaveOne()
        episodeContext = nextEpisode
        val observation = runtime.observeFightCave(nextEpisode.player, includeFutureLeakage = false)
        episodeStartTick = observation.tick
        val state =
            snapshot(
                context = nextEpisode,
                observation = observation,
                request = null,
                lastAction = null,
                lastActionResult = null,
                previousState = currentState,
            )
        currentState = state
        return state
    }

    fun currentState(): DemoLiteTickState =
        checkNotNull(currentState) { "Demo-lite session has not been started yet." }

    fun starterStateManifest(): DemoLiteStarterStateManifest {
        val context = checkNotNull(episodeContext) { "Demo-lite session is not initialized." }
        val state = currentState()
        return DemoLiteStarterStateManifest.capture(context, state.observation)
    }

    fun step(request: DemoLiteActionRequest?): DemoLiteTickState {
        val context = checkNotNull(episodeContext) { "Demo-lite session is not initialized." }
        val resolvedAction = request?.action ?: HeadlessAction.Wait
        val actionResult = runtime.applyFightCaveAction(context.player, resolvedAction)
        runtime.tick()
        val observation = runtime.observeFightCave(context.player, includeFutureLeakage = false)
        val state =
            snapshot(
                context = context,
                observation = observation,
                request = request,
                lastAction = resolvedAction,
                lastActionResult = actionResult,
                previousState = currentState,
            )
        currentState = state
        return state
    }

    internal fun currentEpisodeContextForValidation(): DemoLiteEpisodeContext =
        checkNotNull(episodeContext) { "Demo-lite session is not initialized." }

    private fun snapshot(
        context: DemoLiteEpisodeContext,
        observation: HeadlessObservationV1,
        request: DemoLiteActionRequest?,
        lastAction: HeadlessAction?,
        lastActionResult: HeadlessActionResult?,
        previousState: DemoLiteTickState?,
    ): DemoLiteTickState {
        val visibleTargets = runtime.visibleFightCaveNpcTargets(context.player)
        val trackedNpcs = trackedNpcs(context, visibleTargets)
        val movement = movementSnapshot(context, observation, request, lastAction, lastActionResult, previousState)
        val terminalCode =
            DemoLiteWaveLogic.evaluateTerminalCode(
                observation = observation,
                inFightCaveInstance = context.player.instance() != null,
                episodeStartTick = episodeStartTick,
                tickCap = config.tickCap,
            )
        return DemoLiteTickState(
            observation = observation,
            visibleTargets = visibleTargets,
            trackedNpcs = trackedNpcs,
            movement = movement,
            lastAction = lastAction,
            lastActionResult = lastActionResult,
            terminalCode = terminalCode,
            resetCount = context.resetCount,
            sessionSeed = context.episodeConfig.seed,
            tickCap = config.tickCap,
        )
    }

    private fun trackedNpcs(
        context: DemoLiteEpisodeContext,
        visibleTargets: List<HeadlessVisibleNpcTarget>,
    ): List<DemoLiteTrackedNpcSnapshot> {
        val visibleNpcIndexes = visibleTargets.mapTo(linkedSetOf()) { it.npcIndex }
        val source =
            context.player.instance()?.let { instance ->
                buildList {
                    for (level in 0..3) {
                        addAll(NPCs.at(instance.toLevel(level)))
                    }
                }
            } ?: NPCs.at(context.player.tile.regionLevel)
        return source
            .asSequence()
            .filter { it.index != -1 }
            .distinctBy { it.index }
            .sortedWith(compareBy<NPC>({ it.index }, { it.id }, { it.tile.level }, { it.tile.x }, { it.tile.y }))
            .map { npc ->
                DemoLiteTrackedNpcSnapshot(
                    npcIndex = npc.index,
                    id = npc.id,
                    tile = HeadlessObservationTile.from(npc.tile),
                    hidden = npc.hide,
                    dead = npc.contains("dead") || npc.levels.get(Skill.Constitution) <= 0,
                    underAttack = npc.hasClock("under_attack"),
                    visibleToPlayer = npc.index in visibleNpcIndexes,
                )
            }
            .toList()
    }

    private fun movementSnapshot(
        context: DemoLiteEpisodeContext,
        observation: HeadlessObservationV1,
        request: DemoLiteActionRequest?,
        lastAction: HeadlessAction?,
        lastActionResult: HeadlessActionResult?,
        previousState: DemoLiteTickState?,
    ): DemoLiteMovementSnapshot {
        val currentTile = observation.player.tile
        val destinationTile =
            context.player.steps.destination
                .takeUnless { it == Tile.EMPTY }
                ?.let(HeadlessObservationTile::from)
        val previousDestination = previousState?.movement?.destinationTile
        val movementState =
            when {
                destinationTile != null && destinationTile != currentTile -> "pathing"
                previousDestination != null && destinationTile == null && previousDestination == currentTile -> "arrived"
                previousDestination != null && destinationTile == null && previousDestination != currentTile -> "interrupted"
                destinationTile != null && destinationTile == currentTile -> "arrived"
                else -> "idle"
            }
        val continuingResetSpawn =
            destinationTile != null &&
                request == null &&
                previousState != null &&
                previousState.resetCount == context.resetCount &&
                previousState.observation.tick == episodeStartTick &&
                previousState.movement.destinationTile == null
        val movementSource =
            when {
                destinationTile != null && lastAction == null && lastActionResult == null -> "reset_spawn"
                continuingResetSpawn -> "reset_spawn"
                destinationTile != null && lastActionResult?.actionApplied == true && lastAction is HeadlessAction.WalkToTile -> "movement_button"
                destinationTile != null && lastActionResult?.actionApplied == true && lastAction is HeadlessAction.AttackVisibleNpc -> "attack_pathing"
                previousState?.movement?.destinationTile != null -> previousState.movement.movementSource
                else -> "other"
            }
        val movementSourceDetail =
            when {
                movementSource == "reset_spawn" ->
                    "FightCaveEpisodeInitializer.resetFightCaveInstance -> tele(entrance) + walkTo(fightCave.centre)"
                destinationTile != null && lastActionResult?.actionApplied == true && request != null -> request.source
                previousState?.movement?.destinationTile != null -> previousState.movement.movementSourceDetail
                else -> null
            }
        return DemoLiteMovementSnapshot(
            currentTile = currentTile,
            destinationTile = destinationTile,
            movementState = movementState,
            movementSource = movementSource,
            movementSourceDetail = movementSourceDetail,
            mode = context.player.mode::class.simpleName ?: if (context.player.mode is Movement) "Movement" else "Unknown",
        )
    }
}
