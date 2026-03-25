package sim.engine

import com.github.michaelbull.logging.InlineLogger
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class GameLoop(
    private val stages: List<Runnable>,
    private val delay: Long = ENGINE_DELAY,
) {
    private val logger = InlineLogger()

    /**
     * Per-stage timing from the most recent [tick] call.
     * Index `i` corresponds to `stages[i]`. Updated in-place each tick.
     */
    val stageTimingNanos: LongArray = LongArray(stages.size)

    val stageCount: Int get() = stages.size

    fun start(scope: CoroutineScope) = scope.launch {
        var start: Long
        var took: Long
        try {
            while (isActive) {
                start = System.nanoTime()
                tick()
                took = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                if (took > MILLI_WARNING_THRESHOLD) {
                    logger.warn { "Tick $tick took ${took}ms" }
                } else if (took > MILLI_THRESHOLD) {
                    logger.debug { "Tick $tick took ${took}ms" }
                }
                delay(delay - took)
                tick++
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                logger.error(e) { "Error in game loop!" }
            }
        }
    }

    fun tick() {
        for (i in stages.indices) {
            val stageStart = System.nanoTime()
            stages[i].run()
            val stageNanos = System.nanoTime() - stageStart
            stageTimingNanos[i] = stageNanos
            val stageTook = TimeUnit.NANOSECONDS.toMillis(stageNanos)
            if (stageTook > MILLI_WARNING_THRESHOLD) {
                logger.warn { "${stages[i]::class.simpleName} took ${stageTook}ms" }
            } else if (stageTook > MILLI_THRESHOLD) {
                logger.debug { "${stages[i]::class.simpleName} took ${stageTook}ms" }
            }
        }
    }

    fun tick(stage: Runnable) {
        val stageStart = System.nanoTime()
        stage.run()
        val stageTook = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stageStart)
        if (stageTook > MILLI_WARNING_THRESHOLD) {
            logger.warn { "${stage::class.simpleName} took ${stageTook}ms" }
        } else if (stageTook > MILLI_THRESHOLD) {
            logger.debug { "${stage::class.simpleName} took ${stageTook}ms" }
        }
    }

    fun stageTimingSnapshot(): LongArray = stageTimingNanos.copyOf()

    companion object {
        var tick: Int = 0
        private const val ENGINE_DELAY = 600L
        private const val MILLI_THRESHOLD = 0L
        private const val MILLI_WARNING_THRESHOLD = 100L
    }
}
