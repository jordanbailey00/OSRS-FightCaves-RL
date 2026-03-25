import java.util.concurrent.atomic.AtomicBoolean

class DemoLiteConfig(
    val tickCap: Int = 20_000,
    val tickMillis: Long = 600L,
    val autoResetDelayMillis: Long = 2_000L,
    val startWave: Int = 1,
    val seedBase: Long = 91_000L,
    val accountNamePrefix: String = "demo-lite",
)

class DemoLiteRuntime private constructor(
    private val oracleRuntime: OracleRuntime,
    private val combatLoop: DemoLiteCombatLoop,
    private val sessionLogger: DemoLiteSessionLogger,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    fun sessionId(): String = sessionLogger.sessionId

    fun addSessionEventListener(
        replayExisting: Boolean = true,
        listener: (DemoLiteSessionEvent) -> Unit,
    ) {
        sessionLogger.addEventListener(replayExisting = replayExisting, listener = listener)
    }

    fun start(listener: (DemoLiteTickState) -> Unit) {
        combatLoop.addListener(listener)
        combatLoop.start()
    }

    fun queueAction(
        action: HeadlessAction,
        source: String = "runtime.api",
        payload: Map<String, Any?> = emptyMap(),
    ) {
        combatLoop.queueAction(action, source = source, payload = payload)
    }

    fun requestReset(source: String = "runtime.api") {
        combatLoop.requestReset(source)
    }

    override fun close() {
        close(reason = "runtime_shutdown")
    }

    fun close(reason: String) {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        val lastState = combatLoop.close(reason)
        sessionLogger.closeWithReason(reason = reason, state = lastState)
        oracleRuntime.shutdown()
    }

    companion object {
        fun create(config: DemoLiteConfig = DemoLiteConfig()): DemoLiteRuntime {
            val sessionLogger = DemoLiteSessionLogger.create(config)
            println("Demo-lite session log directory: ${sessionLogger.sessionDirectory}")
            sessionLogger.logSessionStart()
            try {
                val runtime =
                    OracleMain.bootstrap(
                        loadContentScripts = true,
                        startWorld = true,
                        installShutdownHook = false,
                        settingsOverrides = DemoLiteScopePolicy.runtimeSettingsOverrides,
                    )
                sessionLogger.logRuntimeInitialized()
                val episodeInitializer = DemoLiteEpisodeInitializer(runtime, config)
                val session = DemoLiteFightCavesSession(runtime, episodeInitializer, config)
                val resetController = DemoLiteResetController(config.autoResetDelayMillis)
                val combatLoop = DemoLiteCombatLoop(session, resetController, config.tickMillis, sessionLogger)
                return DemoLiteRuntime(runtime, combatLoop, sessionLogger)
            } catch (error: Throwable) {
                sessionLogger.logBackendEvent(
                    eventType = "startup_error",
                    fields =
                        linkedMapOf(
                            "message" to error.message,
                            "error_type" to error::class.qualifiedName,
                        ),
                )
                sessionLogger.closeWithReason(reason = "startup_error")
                throw error
            }
        }
    }
}
