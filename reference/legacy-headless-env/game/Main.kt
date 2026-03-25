import com.github.michaelbull.logging.InlineLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import sim.cache.Cache
import sim.cache.secure.Huffman
import sim.engine.*
import sim.engine.client.PlayerAccountLoader
import sim.engine.data.*
import sim.engine.entity.World
import sim.engine.event.AuditLog
import sim.network.GameServer
import sim.network.LoginServer
import sim.network.login.protocol.decoders

/**
 * @author GregHib <greg@gregs.world>
 * @since April 18, 2020
 */
object Main {

    private val logger = InlineLogger()
    lateinit var server: GameServer
        private set

    @JvmStatic
    fun main(args: Array<String>) {
        AuditLog.info("startup")
        val startTime = System.currentTimeMillis()
        val settings = loadRuntimeSettings(RuntimeMode.Headed)

        // File server
        val cache = timed("cache") { Cache.load(settings) }
        server = GameServer.load(cache, settings)
        val job = server.start(Settings["network.port"].toInt())
        AuditLog.info("login online")

        // Content
        val configFiles = configFiles()
        try {
            preloadRuntime(cache, configFiles, RuntimeMode.Headed)
        } catch (ex: Exception) {
            logger.error(ex) { "Error loading files." }
            server.stop()
            return
        }

        // Login server
        val decoderPipeline = decoders(get<Huffman>())
        val accountLoader: PlayerAccountLoader = get()
        val loginServer = LoginServer.load(settings, decoderPipeline, accountLoader)

        // Game world
        val stages = getTickStages()
        World.start(configFiles)
        val scope = CoroutineScope(Contexts.Game)
        val engine = GameLoop(stages).start(scope)
        server.loginServer = loginServer
        logger.info {
            "${Settings["server.name"]} loaded in ${System.currentTimeMillis() - startTime}ms (mode=${currentRuntimeMode()})"
        }
        AuditLog.info("game online")

        runBlocking {
            try {
                job.join()
            } finally {
                engine.cancel()
                server.stop()
                AuditLog.info("game offline")
            }
        }
    }
}
