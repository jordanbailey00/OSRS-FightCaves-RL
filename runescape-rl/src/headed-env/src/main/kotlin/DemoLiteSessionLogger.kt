import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

data class DemoLiteActionRequest(
    val requestId: Long,
    val label: String,
    val source: String,
    val action: HeadlessAction,
    val payload: Map<String, Any?> = emptyMap(),
    val requestedAtMillis: Long = System.currentTimeMillis(),
    val requestedAtTick: Int? = null,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any?> =
        linkedMapOf(
            "request_id" to requestId,
            "label" to label,
            "source" to source,
            "action" to action.toOrderedMap(),
            "payload" to payload,
            "requested_at_millis" to requestedAtMillis,
            "requested_at_tick" to requestedAtTick,
        )
}

class DemoLiteSessionLogger private constructor(
    val sessionId: String,
    val sessionDirectory: Path,
    private val config: DemoLiteConfig,
) : AutoCloseable {
    val sessionLogPath: Path = sessionDirectory.resolve("session_log.jsonl")
    val sessionSummaryPath: Path = sessionDirectory.resolve("session_summary.json")

    private val writer =
        Files.newBufferedWriter(
            sessionLogPath,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    private val startedAtMillis = System.currentTimeMillis()
    private var eventCount: Long = 0
    private var tickCount: Long = 0
    private var actionRequestCount: Long = 0
    private var actionHandledCount: Long = 0
    private var actionRejectedCount: Long = 0
    private var actionDiscardedCount: Long = 0
    private var currentEpisodeId: String? = null
    private var sessionEndReason: String? = null
    private val terminalCounts = linkedMapOf<String, Int>()
    private val resetCounts = linkedMapOf<String, Int>()
    private val discardedActionCounts = linkedMapOf<String, Int>()
    private val starterStateManifestPaths = linkedMapOf<String, String>()
    private val eventHistory = CopyOnWriteArrayList<DemoLiteSessionEvent>()
    private val eventListeners = CopyOnWriteArrayList<(DemoLiteSessionEvent) -> Unit>()
    private val closed = AtomicBoolean(false)

    fun addEventListener(
        replayExisting: Boolean = true,
        listener: (DemoLiteSessionEvent) -> Unit,
    ) {
        if (replayExisting) {
            eventHistory.forEach(listener)
        }
        eventListeners += listener
    }

    fun logSessionStart() {
        logEvent(
            eventType = "session_start",
            fields =
                linkedMapOf(
                    "session_directory" to sessionDirectory.toString(),
                    "session_log_path" to sessionLogPath.toString(),
                    "session_summary_path" to sessionSummaryPath.toString(),
                    "tick_millis" to config.tickMillis,
                    "tick_cap" to config.tickCap,
                    "start_wave" to config.startWave,
                ),
        )
        logEvent(
            eventType = "scope_policy",
            fields = linkedMapOf("runtime_settings_overrides" to DemoLiteScopePolicy.runtimeSettingsOverrides),
        )
    }

    fun logRuntimeInitialized() {
        logEvent(eventType = "runtime_initialized")
    }

    fun logInputRequested(request: DemoLiteActionRequest, currentState: DemoLiteTickState?) {
        actionRequestCount += 1
        logEvent(
            eventType = "input_requested",
            episodeId = episodeIdFor(currentState),
            tick = currentState?.observation?.tick,
            fields =
                linkedMapOf(
                    "request" to request.toOrderedMap(),
                ),
        )
    }

    fun logInputDiscarded(
        request: DemoLiteActionRequest,
        reason: String,
        currentState: DemoLiteTickState?,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        actionDiscardedCount += 1
        increment(discardedActionCounts, reason)
        logEvent(
            eventType = "input_discarded",
            episodeId = episodeIdFor(currentState),
            tick = currentState?.observation?.tick,
            fields =
                linkedMapOf<String, Any?>(
                    "discard_reason" to reason,
                    "request" to request.toOrderedMap(),
                ).apply {
                    putAll(fields)
                },
        )
    }

    fun logManualResetRequested(source: String, currentState: DemoLiteTickState?) {
        logEvent(
            eventType = "reset_requested",
            episodeId = episodeIdFor(currentState),
            tick = currentState?.observation?.tick,
            fields =
                linkedMapOf(
                    "source" to source,
                    "terminal_code" to currentState?.terminalCode?.name,
                ),
        )
    }

    fun logResetDecision(
        decision: DemoLiteResetDecision,
        state: DemoLiteTickState,
        phase: String,
    ) {
        if (!decision.resetNow && decision.autoResetRemainingMillis == null) {
            return
        }
        logEvent(
            eventType = "reset_decision",
            episodeId = episodeIdFor(state),
            tick = state.observation.tick,
            fields =
                linkedMapOf(
                    "runtime_phase" to phase,
                    "reset_now" to decision.resetNow,
                    "reason" to decision.reason,
                    "auto_reset_remaining_millis" to decision.autoResetRemainingMillis,
                    "terminal_code" to state.terminalCode.name,
                ),
        )
    }

    fun logResetBegin(cause: String, previousState: DemoLiteTickState?) {
        increment(resetCounts, cause)
        logEvent(
            eventType = "reset_begin",
            episodeId = episodeIdFor(previousState),
            tick = previousState?.observation?.tick,
            fields =
                linkedMapOf(
                    "cause" to cause,
                    "previous_terminal_code" to previousState?.terminalCode?.name,
                ),
        )
    }

    fun writeStarterStateManifest(episodeId: String, manifest: DemoLiteStarterStateManifest): Path {
        val path = sessionDirectory.resolve("${episodeId}_starter_state_manifest.json")
        Files.writeString(path, DemoLiteJson.encodePretty(manifest.toOrderedMap()) + "\n")
        starterStateManifestPaths[episodeId] = path.toString()
        logEvent(
            eventType = "starter_state_manifest_written",
            episodeId = episodeId,
            fields =
                linkedMapOf(
                    "path" to path.toString(),
                    "manifest" to manifest.toOrderedMap(),
                ),
        )
        return path
    }

    fun logResetComplete(
        cause: String,
        state: DemoLiteTickState,
        manifestPath: Path,
    ) {
        currentEpisodeId = episodeIdFor(state)
        logEvent(
            eventType = "reset_complete",
            episodeId = episodeIdFor(state),
            tick = state.observation.tick,
            fields =
                linkedMapOf(
                    "cause" to cause,
                    "manifest_path" to manifestPath.toString(),
                    "wave" to state.observation.wave.wave,
                    "rotation" to state.observation.wave.rotation,
                    "remaining" to state.observation.wave.remaining,
                    "terminal_code" to state.terminalCode.name,
                ),
        )
    }

    fun logStartupMovementIntent(state: DemoLiteTickState) {
        val movement = state.movement
        if (movement.movementSource != "reset_spawn" || movement.destinationTile == null) {
            return
        }
        logEvent(
            eventType = "startup_movement_intent",
            episodeId = episodeIdFor(state),
            tick = state.observation.tick,
            fields =
                linkedMapOf(
                    "intentional_shared_runtime_behavior" to true,
                    "shared_initializer_path" to "FightCaveEpisodeInitializer.resetFightCaveInstance",
                    "current_tile" to movement.currentTile.toOrderedMap(),
                    "destination_tile" to movement.destinationTile.toOrderedMap(),
                    "movement_state" to movement.movementState,
                    "movement_source" to movement.movementSource,
                    "movement_source_detail" to movement.movementSourceDetail,
                    "note" to "Shared reset path teleports to the Fight Caves entrance and immediately walkTo()s the cave centre before wave start.",
                ),
        )
    }

    fun logActionHandled(
        phase: String,
        request: DemoLiteActionRequest?,
        state: DemoLiteTickState,
    ) {
        val result = state.lastActionResult
        val source = request?.source ?: "runtime.idle_wait"
        val label = request?.label ?: "wait"
        val deferred = request?.requestedAtTick?.let { it != state.observation.tick } ?: false
        actionHandledCount += 1
        if (result?.actionApplied == false) {
            actionRejectedCount += 1
        }
        logEvent(
            eventType = "action_handled",
            episodeId = episodeIdFor(state),
            tick = state.observation.tick,
            fields =
                linkedMapOf(
                    "runtime_phase" to phase,
                    "request_id" to request?.requestId,
                    "source" to source,
                    "label" to label,
                    "deferred" to deferred,
                    "requested_at_tick" to request?.requestedAtTick,
                    "action" to (request?.action?.toOrderedMap() ?: HeadlessAction.Wait.toOrderedMap()),
                    "action_result" to actionResultMap(result),
                    "immediate_metadata" to (result?.metadata ?: emptyMap<String, String>()),
                    "movement" to state.movement.toOrderedMap(),
                ),
        )
    }

    fun logTick(
        phase: String,
        previousState: DemoLiteTickState?,
        state: DemoLiteTickState,
        request: DemoLiteActionRequest?,
    ) {
        tickCount += 1
        logTerminalIfNeeded(previousState, state, phase)
        val startupMovementBegan =
            state.movement.movementSource == "reset_spawn" &&
                previousState?.movement?.movementSource != "reset_spawn"
        if (previousState == null || previousState.resetCount != state.resetCount || startupMovementBegan) {
            logStartupMovementIntent(state)
        }
        val deltas = stateDeltas(previousState, state)
        if (deltas.isNotEmpty()) {
            logEvent(
                eventType = "state_delta",
                episodeId = episodeIdFor(state),
                tick = state.observation.tick,
                fields =
                    linkedMapOf(
                        "runtime_phase" to phase,
                        "changes" to deltas,
                    ),
            )
        }
        val player = state.observation.player
        logEvent(
            eventType = "tick_state",
            episodeId = episodeIdFor(state),
            tick = state.observation.tick,
            fields =
                linkedMapOf(
                    "runtime_phase" to phase,
                    "player_idle" to isIdle(state),
                    "player_busy" to isBusy(state),
                    "lockouts" to state.observation.player.lockouts.toOrderedMap(),
                    "visible_targets" to visibleTargets(state),
                    "selected_target" to selectedTarget(state),
                    "jad_telegraph_state" to DemoLiteJadTelegraph.describe(state.observation),
                    "prayers" to state.observation.player.protectionPrayers.toOrderedMap(),
                    "run_enabled" to player.running,
                    "player_tile" to player.tile.toOrderedMap(),
                    "movement" to state.movement.toOrderedMap(),
                    "hitpoints_current" to player.hitpointsCurrent,
                    "hitpoints_max" to player.hitpointsMax,
                    "prayer_current" to player.prayerCurrent,
                    "prayer_max" to player.prayerMax,
                    "ammo_count" to player.consumables.ammoCount,
                    "shark_count" to player.consumables.sharkCount,
                    "prayer_potion_doses_remaining" to player.consumables.prayerPotionDoseCount,
                    "wave" to state.observation.wave.wave,
                    "rotation" to state.observation.wave.rotation,
                    "remaining" to state.observation.wave.remaining,
                    "tracked_npcs" to state.trackedNpcs.map { it.toOrderedMap() },
                    "terminal_code" to state.terminalCode.name,
                    "auto_reset_remaining_millis" to state.autoResetRemainingMillis,
                    "action_request" to request?.toOrderedMap(),
                    "action_result" to actionResultMap(state.lastActionResult),
                ),
        )
    }

    fun logBackendEvent(
        eventType: String,
        state: DemoLiteTickState? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        logEvent(
            eventType = eventType,
            episodeId = episodeIdFor(state),
            tick = state?.observation?.tick,
            fields = LinkedHashMap(fields),
        )
    }

    fun closeWithReason(reason: String, state: DemoLiteTickState? = null) {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        sessionEndReason = reason
        logEvent(
            eventType = "session_end",
            episodeId = episodeIdFor(state),
            tick = state?.observation?.tick,
            fields =
                linkedMapOf(
                    "session_end_reason" to reason,
                    "last_terminal_code" to state?.terminalCode?.name,
                    "last_wave" to state?.observation?.wave?.wave,
                    "duration_millis" to (System.currentTimeMillis() - startedAtMillis),
                ),
        )
        writer.close()
    }

    override fun close() {
        closeWithReason(reason = "runtime_shutdown")
    }

    private fun logTerminalIfNeeded(previousState: DemoLiteTickState?, state: DemoLiteTickState, phase: String) {
        if (state.terminalCode == DemoLiteTerminalCode.NONE) {
            return
        }
        if (previousState?.terminalCode == state.terminalCode) {
            return
        }
        increment(terminalCounts, state.terminalCode.name)
        logEvent(
            eventType = "terminal_emitted",
            episodeId = episodeIdFor(state),
            tick = state.observation.tick,
            fields =
                linkedMapOf(
                    "runtime_phase" to phase,
                    "terminal_code" to state.terminalCode.name,
                    "wave" to state.observation.wave.wave,
                    "remaining" to state.observation.wave.remaining,
                ),
        )
    }

    private fun logEvent(
        eventType: String,
        episodeId: String? = currentEpisodeId,
        tick: Int? = null,
        fields: LinkedHashMap<String, Any?> = linkedMapOf(),
    ) {
        val payload =
            linkedMapOf<String, Any?>(
                "event_type" to eventType,
                "timestamp_millis" to System.currentTimeMillis(),
                "session_id" to sessionId,
                "episode_id" to episodeId,
                "tick" to tick,
            )
        payload.putAll(fields)
        writer.write(DemoLiteJson.encode(payload))
        writer.newLine()
        writer.flush()
        val event =
            DemoLiteSessionEvent(
                eventType = eventType,
                timestampMillis = payload["timestamp_millis"] as Long,
                sessionId = sessionId,
                episodeId = episodeId,
                tick = tick,
                payload = LinkedHashMap(payload),
            )
        eventHistory += event
        eventListeners.forEach { listener -> listener(event) }
        eventCount += 1
        writeSummary()
    }

    private fun writeSummary() {
        val payload =
            linkedMapOf(
                "session_id" to sessionId,
                "started_at_millis" to startedAtMillis,
                "session_directory" to sessionDirectory.toString(),
                "session_log_path" to sessionLogPath.toString(),
                "session_summary_path" to sessionSummaryPath.toString(),
                "event_count" to eventCount,
                "tick_count" to tickCount,
                "action_request_count" to actionRequestCount,
                "action_handled_count" to actionHandledCount,
                "action_rejected_count" to actionRejectedCount,
                "action_discarded_count" to actionDiscardedCount,
                "current_episode_id" to currentEpisodeId,
                "session_end_reason" to sessionEndReason,
                "terminal_counts" to terminalCounts,
                "reset_counts" to resetCounts,
                "discarded_action_counts" to discardedActionCounts,
                "starter_state_manifest_paths" to starterStateManifestPaths,
                "tick_millis" to config.tickMillis,
                "tick_cap" to config.tickCap,
                "start_wave" to config.startWave,
            )
        Files.writeString(sessionSummaryPath, DemoLiteJson.encodePretty(payload) + "\n")
    }

    private fun isIdle(state: DemoLiteTickState): Boolean {
        val lockouts = state.observation.player.lockouts
        return !lockouts.attackLocked &&
            !lockouts.foodLocked &&
            !lockouts.drinkLocked &&
            !lockouts.comboLocked &&
            !lockouts.busyLocked
    }

    private fun isBusy(state: DemoLiteTickState): Boolean = !isIdle(state)

    private fun visibleTargets(state: DemoLiteTickState): List<Map<String, Any?>> =
        state.visibleTargets.map { target ->
            val npc = state.observation.npcs.firstOrNull { it.visibleIndex == target.visibleIndex }
            linkedMapOf(
                "visible_index" to target.visibleIndex,
                "npc_index" to target.npcIndex,
                "id" to target.id,
                "tile" to linkedMapOf("x" to target.tile.x, "y" to target.tile.y, "level" to target.tile.level),
                "hitpoints_current" to npc?.hitpointsCurrent,
                "hitpoints_max" to npc?.hitpointsMax,
            )
        }

    private fun selectedTarget(state: DemoLiteTickState): Map<String, Any?>? =
        when (val action = state.lastAction) {
            is HeadlessAction.AttackVisibleNpc -> {
                val target = state.visibleTargets.getOrNull(action.visibleNpcIndex)
                linkedMapOf(
                    "visible_index" to action.visibleNpcIndex,
                    "npc_index" to target?.npcIndex,
                    "id" to target?.id,
                )
            }
            else -> null
        }

    private fun stateDeltas(previous: DemoLiteTickState?, current: DemoLiteTickState): List<Map<String, Any?>> {
        if (previous == null || previous.resetCount != current.resetCount) {
            val startup = mutableListOf<Map<String, Any?>>(
                linkedMapOf(
                    "field" to "episode_start",
                    "from" to null,
                    "to" to linkedMapOf(
                        "wave" to current.observation.wave.wave,
                        "tile" to current.observation.player.tile.toOrderedMap(),
                        ),
                )
            )
            if (current.trackedNpcs.isNotEmpty()) {
                startup +=
                    linkedMapOf(
                        "field" to "npc_tracking_added",
                        "from" to null,
                        "to" to
                            current.trackedNpcs.map { npc ->
                                linkedMapOf(
                                    "classification" to "wave_start_world_spawn",
                                    "npc" to npc.toOrderedMap(),
                                )
                            },
                    )
            }
            return startup
        }
        val deltas = mutableListOf<Map<String, Any?>>()
        maybeAddDelta(deltas, "hitpoints_current", previous.observation.player.hitpointsCurrent, current.observation.player.hitpointsCurrent)
        maybeAddDelta(deltas, "prayer_current", previous.observation.player.prayerCurrent, current.observation.player.prayerCurrent)
        maybeAddDelta(deltas, "ammo_count", previous.observation.player.consumables.ammoCount, current.observation.player.consumables.ammoCount)
        maybeAddDelta(deltas, "shark_count", previous.observation.player.consumables.sharkCount, current.observation.player.consumables.sharkCount)
        maybeAddDelta(
            deltas,
            "prayer_potion_doses_remaining",
            previous.observation.player.consumables.prayerPotionDoseCount,
            current.observation.player.consumables.prayerPotionDoseCount,
        )
        maybeAddDelta(deltas, "run_enabled", previous.observation.player.running, current.observation.player.running)
        maybeAddDelta(deltas, "wave", previous.observation.wave.wave, current.observation.wave.wave)
        maybeAddDelta(deltas, "remaining", previous.observation.wave.remaining, current.observation.wave.remaining)
        maybeAddDelta(deltas, "terminal_code", previous.terminalCode.name, current.terminalCode.name)
        maybeAddDelta(deltas, "jad_telegraph_state", DemoLiteJadTelegraph.describe(previous.observation), DemoLiteJadTelegraph.describe(current.observation))
        maybeAddDelta(deltas, "movement_state", previous.movement.movementState, current.movement.movementState)
        maybeAddDelta(deltas, "movement_source", previous.movement.movementSource, current.movement.movementSource)
        if (previous.observation.player.tile != current.observation.player.tile) {
            deltas +=
                linkedMapOf(
                    "field" to "player_tile",
                    "from" to previous.observation.player.tile.toOrderedMap(),
                    "to" to current.observation.player.tile.toOrderedMap(),
                )
        }
        if (previous.movement.destinationTile != current.movement.destinationTile) {
            deltas +=
                linkedMapOf(
                    "field" to "movement_destination_tile",
                    "from" to previous.movement.destinationTile?.toOrderedMap(),
                    "to" to current.movement.destinationTile?.toOrderedMap(),
                )
        }
        if (previous.movement.destinationTile != null && current.movement.movementState == "arrived") {
            deltas +=
                linkedMapOf(
                    "field" to "movement_arrived",
                    "from" to previous.movement.toOrderedMap(),
                    "to" to current.movement.toOrderedMap(),
                )
        } else if (previous.movement.destinationTile != null && current.movement.movementState == "interrupted") {
            deltas +=
                linkedMapOf(
                    "field" to "movement_interrupted",
                    "from" to previous.movement.toOrderedMap(),
                    "to" to current.movement.toOrderedMap(),
                )
        }
        if (previous.observation.player.protectionPrayers.toOrderedMap() != current.observation.player.protectionPrayers.toOrderedMap()) {
            deltas +=
                linkedMapOf(
                    "field" to "protection_prayers",
                    "from" to previous.observation.player.protectionPrayers.toOrderedMap(),
                    "to" to current.observation.player.protectionPrayers.toOrderedMap(),
                )
        }
        val previousTracked = previous.trackedNpcs.associateBy { it.npcIndex }
        val currentTracked = current.trackedNpcs.associateBy { it.npcIndex }
        val trackedAdded =
            currentTracked.keys.minus(previousTracked.keys).map { npcIndex ->
                val npc = currentTracked.getValue(npcIndex)
                linkedMapOf(
                    "classification" to classifyNpcAddition(previous, current, npc),
                    "npc" to npc.toOrderedMap(),
                )
            }
        val trackedRemoved =
            previousTracked.keys.minus(currentTracked.keys).map { npcIndex ->
                val npc = previousTracked.getValue(npcIndex)
                linkedMapOf(
                    "classification" to "despawned",
                    "npc" to npc.toOrderedMap(),
                )
            }
        val newlyVisible =
            currentTracked.keys.intersect(previousTracked.keys).mapNotNull { npcIndex ->
                val before = previousTracked.getValue(npcIndex)
                val after = currentTracked.getValue(npcIndex)
                if (!before.visibleToPlayer && after.visibleToPlayer) {
                    linkedMapOf(
                        "classification" to "newly_visible",
                        "npc" to after.toOrderedMap(),
                    )
                } else {
                    null
                }
            }
        val noLongerVisible =
            currentTracked.keys.intersect(previousTracked.keys).mapNotNull { npcIndex ->
                val before = previousTracked.getValue(npcIndex)
                val after = currentTracked.getValue(npcIndex)
                if (before.visibleToPlayer && !after.visibleToPlayer) {
                    linkedMapOf(
                        "classification" to "no_longer_visible",
                        "npc" to after.toOrderedMap(),
                    )
                } else {
                    null
                }
            }
        val defeated =
            currentTracked.keys.intersect(previousTracked.keys).mapNotNull { npcIndex ->
                val before = previousTracked.getValue(npcIndex)
                val after = currentTracked.getValue(npcIndex)
                if (!before.dead && after.dead) after.toOrderedMap() else null
            }
        if (trackedAdded.isNotEmpty()) {
            deltas += linkedMapOf("field" to "npc_tracking_added", "from" to null, "to" to trackedAdded)
        }
        if (newlyVisible.isNotEmpty()) {
            deltas += linkedMapOf("field" to "npc_newly_visible", "from" to null, "to" to newlyVisible)
        }
        if (noLongerVisible.isNotEmpty()) {
            deltas += linkedMapOf("field" to "npc_no_longer_visible", "from" to noLongerVisible, "to" to null)
        }
        if (trackedRemoved.isNotEmpty()) {
            deltas += linkedMapOf("field" to "npc_tracking_removed", "from" to trackedRemoved, "to" to null)
        }
        if (defeated.isNotEmpty()) {
            deltas += linkedMapOf("field" to "npc_defeated", "from" to null, "to" to defeated)
        }
        return deltas
    }

    private fun classifyNpcAddition(
        previous: DemoLiteTickState,
        current: DemoLiteTickState,
        npc: DemoLiteTrackedNpcSnapshot,
    ): String =
        when {
            current.observation.wave.wave != previous.observation.wave.wave -> "wave_start_world_spawn"
            npc.id.contains("_spawn") -> "split_spawn"
            else -> "newly_tracked_npc"
        }

    private fun maybeAddDelta(
        deltas: MutableList<Map<String, Any?>>,
        field: String,
        previous: Any?,
        current: Any?,
    ) {
        if (previous == current) {
            return
        }
        deltas += linkedMapOf("field" to field, "from" to previous, "to" to current)
    }

    private fun actionResultMap(result: HeadlessActionResult?): Map<String, Any?>? =
        result?.let {
            linkedMapOf(
                "action_type" to it.actionType.name,
                "action_id" to it.actionId,
                "action_applied" to it.actionApplied,
                "rejection_reason" to it.rejectionReason?.name,
                "metadata" to it.metadata,
            )
        }

    private fun episodeIdFor(state: DemoLiteTickState?): String? =
        state?.let { "episode-${it.resetCount.toString().padStart(4, '0')}" } ?: currentEpisodeId

    private fun increment(map: MutableMap<String, Int>, key: String) {
        map[key] = (map[key] ?: 0) + 1
    }

    companion object {
        private val sessionCounter = AtomicLong(0)
        private val timestampFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)

        fun create(config: DemoLiteConfig): DemoLiteSessionLogger {
            val moduleDir =
                Paths.get(
                    System.getProperty("demoLiteModuleDir", "../fight-caves-demo-lite")
                ).toAbsolutePath().normalize()
            val logRoot =
                Paths.get(
                    System.getProperty("demoLiteSessionLogRoot", moduleDir.resolve("session-logs").toString())
                ).toAbsolutePath().normalize()
            Files.createDirectories(logRoot)
            val sessionId =
                "demo-lite-" +
                    timestampFormatter.format(Instant.now()) +
                    "-" + sessionCounter.incrementAndGet().toString().padStart(4, '0')
            val sessionDirectory = logRoot.resolve(sessionId)
            Files.createDirectories(sessionDirectory)
            return DemoLiteSessionLogger(sessionId = sessionId, sessionDirectory = sessionDirectory, config = config)
        }
    }
}

private fun HeadlessAction.toOrderedMap(): LinkedHashMap<String, Any?> =
    when (this) {
        HeadlessAction.Wait ->
            linkedMapOf(
                "type" to type.name,
            )
        is HeadlessAction.WalkToTile ->
            linkedMapOf(
                "type" to type.name,
                "tile" to linkedMapOf("x" to tile.x, "y" to tile.y, "level" to tile.level),
            )
        is HeadlessAction.AttackVisibleNpc ->
            linkedMapOf(
                "type" to type.name,
                "visible_npc_index" to visibleNpcIndex,
            )
        is HeadlessAction.ToggleProtectionPrayer ->
            linkedMapOf(
                "type" to type.name,
                "prayer" to prayer.prayerId,
            )
        HeadlessAction.EatShark ->
            linkedMapOf(
                "type" to type.name,
            )
        HeadlessAction.DrinkPrayerPotion ->
            linkedMapOf(
                "type" to type.name,
            )
        HeadlessAction.ToggleRun ->
            linkedMapOf(
                "type" to type.name,
            )
    }
