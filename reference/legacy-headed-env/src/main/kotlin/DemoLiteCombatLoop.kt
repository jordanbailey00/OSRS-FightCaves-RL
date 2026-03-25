import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class DemoLiteCombatLoop(
    private val session: DemoLiteFightCavesSession,
    private val resetController: DemoLiteResetController,
    private val tickMillis: Long,
    private val logger: DemoLiteSessionLogger,
) : AutoCloseable {
    private val executor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "demo-lite-combat-loop").apply {
                isDaemon = true
            }
        }
    private val listeners = CopyOnWriteArrayList<(DemoLiteTickState) -> Unit>()
    private val pendingAction = AtomicReference<DemoLiteActionRequest?>(null)
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    @Volatile
    private var latestState: DemoLiteTickState? = null

    fun addListener(listener: (DemoLiteTickState) -> Unit) {
        listeners += listener
        latestState?.let(listener)
    }

    fun queueAction(
        action: HeadlessAction,
        source: String,
        payload: Map<String, Any?> = emptyMap(),
    ) {
        if (closed.get()) {
            return
        }
        val current = latestState
        val request =
            DemoLiteActionRequest(
                requestId = System.nanoTime(),
                label = actionLabel(action),
                source = source,
                action = action,
                payload = payload,
                requestedAtTick = current?.observation?.tick,
            )
        val replaced = pendingAction.getAndSet(request)
        if (replaced != null) {
            logger.logInputDiscarded(
                request = replaced,
                reason = "replaced_by_new_input",
                currentState = current,
                fields =
                    linkedMapOf(
                        "replacement_request" to request.toOrderedMap(),
                    ),
            )
        }
        logger.logInputRequested(request, current)
    }

    fun requestReset(source: String) {
        resetController.requestManualReset()
        logger.logManualResetRequested(source, latestState)
    }

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        logger.logBackendEvent("combat_loop_start")
        publish(resetEpisode("startup"), phase = "reset_complete", request = null)
        executor.scheduleAtFixedRate(
            { tickOnce() },
            tickMillis,
            tickMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    override fun close() {
        close(reason = "runtime_shutdown")
    }

    fun close(reason: String): DemoLiteTickState? {
        if (!closed.compareAndSet(false, true)) {
            return latestState
        }
        val current = latestState
        val discarded = pendingAction.getAndSet(null)
        if (discarded != null) {
            logger.logInputDiscarded(
                request = discarded,
                reason = "discarded_due_to_shutdown",
                currentState = current,
                fields = linkedMapOf("shutdown_reason" to reason),
            )
        }
        logger.logBackendEvent(
            eventType = "combat_loop_stop",
            state = current,
            fields = linkedMapOf("reason" to reason),
        )
        executor.shutdownNow()
        return current
    }

    private fun tickOnce() {
        val current = latestState ?: return
        val decisionBefore = resetController.evaluate(current)
        logger.logResetDecision(decisionBefore, current, phase = "pre_tick")
        val nextBase =
            when {
                decisionBefore.resetNow -> {
                    val discarded = pendingAction.getAndSet(null)
                    if (discarded != null) {
                        logger.logInputDiscarded(
                            request = discarded,
                            reason = "discarded_due_to_reset",
                            currentState = current,
                            fields = linkedMapOf("reset_reason" to (decisionBefore.reason ?: "reset")),
                        )
                    }
                    resetEpisode(decisionBefore.reason ?: "reset")
                }
                current.terminalCode != DemoLiteTerminalCode.NONE -> current
                else -> {
                    val request = pendingAction.getAndSet(null)
                    val nextState = session.step(request)
                    logger.logActionHandled(
                        phase = if (request == null) "idle_tick" else "action_tick",
                        request = request,
                        state = nextState,
                    )
                    publish(nextState, phase = if (request == null) "idle_tick" else "action_tick", request = request)
                    return
                }
            }
        val decisionAfter = resetController.evaluate(nextBase)
        logger.logResetDecision(
            decision = decisionAfter,
            state = nextBase,
            phase = if (decisionBefore.resetNow) "reset_complete" else "terminal_hold",
        )
        publish(
            nextBase.copy(
                autoResetRemainingMillis = decisionAfter.autoResetRemainingMillis,
            ),
            phase = if (decisionBefore.resetNow) "reset_complete" else "terminal_hold",
            request = null,
        )
    }

    private fun resetEpisode(cause: String): DemoLiteTickState {
        logger.logResetBegin(cause, latestState)
        val state = session.reset()
        val manifestPath = logger.writeStarterStateManifest("episode-${state.resetCount.toString().padStart(4, '0')}", session.starterStateManifest())
        logger.logResetComplete(cause, state, manifestPath)
        return state
    }

    private fun publish(
        state: DemoLiteTickState,
        phase: String,
        request: DemoLiteActionRequest?,
    ) {
        val previous = latestState
        logger.logTick(phase = phase, previousState = previous, state = state, request = request)
        latestState = state
        listeners.forEach { listener -> listener(state) }
    }

    private fun actionLabel(action: HeadlessAction): String =
        when (action) {
            HeadlessAction.Wait -> "wait"
            HeadlessAction.EatShark -> "eat_shark"
            HeadlessAction.DrinkPrayerPotion -> "drink_prayer"
            HeadlessAction.ToggleRun -> "toggle_run"
            is HeadlessAction.AttackVisibleNpc -> "attack_visible_npc"
            is HeadlessAction.ToggleProtectionPrayer -> "toggle_${action.prayer.prayerId}"
            is HeadlessAction.WalkToTile -> "walk_to_tile"
        }
}
