package world.gregs.voidps.engine.client

import com.github.michaelbull.logging.InlineLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withContext
import world.gregs.voidps.engine.data.AccountManager
import world.gregs.voidps.engine.data.SaveQueue
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.data.Storage
import world.gregs.voidps.engine.data.definition.AccountDefinitions
import world.gregs.voidps.engine.data.copy
import world.gregs.voidps.engine.entity.World
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.name
import world.gregs.voidps.engine.entity.character.player.rights
import world.gregs.voidps.engine.event.AuditLog
import world.gregs.voidps.network.Response
import world.gregs.voidps.network.client.Client
import world.gregs.voidps.network.client.ConnectionQueue
import world.gregs.voidps.network.client.Instruction
import world.gregs.voidps.network.login.AccountLoader
import world.gregs.voidps.network.login.protocol.encode.login

/**
 * Checks password is valid for a player account before logging in
 * Keeps track of the players online, prevents duplicate login attempts
 */
class PlayerAccountLoader(
    private val queue: ConnectionQueue,
    private val storage: Storage,
    private val accounts: AccountManager,
    private val saveQueue: SaveQueue,
    private val accountDefinitions: AccountDefinitions,
    private val gameContext: CoroutineDispatcher,
) : AccountLoader {
    private val logger = InlineLogger()

    var update: Boolean = false

    override fun exists(username: String): Boolean = storage.exists(username)

    override fun password(username: String): String? = accountDefinitions.get(username)?.passwordHash

    /**
     * @return flow of instructions for the player to be controlled with
     */
    override suspend fun load(client: Client, username: String, passwordHash: String, displayMode: Int): SendChannel<Instruction>? {
        try {
            val saving = saveQueue.saving(username)
            if (saving) {
                client.disconnect(Response.ACCOUNT_ONLINE)
                return null
            }
            if (update) {
                client.disconnect(Response.GAME_UPDATE)
                return null
            }
            val existing = storage.load(username)
            var player = existing?.toPlayer()
            val createdNew = player == null
            if (player == null) {
                if (!Settings["development.accountCreation", false]) {
                    client.disconnect(Response.INVALID_CREDENTIALS)
                    return null
                }
                player = accounts.create(username, passwordHash)
                persist(player)
            }
            logger.info { "login_load username=$username save_present=${existing != null} created_new=$createdNew" }
            logger.info { "Player $username loaded and queued for login." }
            connect(player, client, displayMode)
            return player.instructions
        } catch (e: IllegalStateException) {
            logger.trace(e) { "Error loading player account" }
            client.disconnect(Response.COULD_NOT_COMPLETE_LOGIN)
            return null
        }
    }

    private fun persist(player: Player) {
        storage.save(listOf(player.copy()))
        logger.info { "login_persist_new_account username=${player.accountName}" }
    }

    suspend fun connect(player: Player, client: Client, displayMode: Int = 0, viewport: Boolean = true) {
        if (!accounts.setup(player, client, displayMode, viewport)) {
            logger.warn { "Error setting up account" }
            client.disconnect(Response.WORLD_FULL)
            return
        }
        logger.info { "login_connect_setup username=${player.accountName} viewport=$viewport display_mode=$displayMode" }
        withContext(gameContext) {
            logger.info { "login_connect_queue_wait username=${player.accountName}" }
            queue.await()
            logger.info { "login_connect_queue_ready username=${player.accountName}" }
            logger.info { "${if (viewport) "Player" else "Bot"} logged in ${player.accountName} index ${player.index}." }
            client.login(player.name, player.index, player.rights.ordinal, member = World.members, membersWorld = World.members)
            logger.info { "login_connect_client_login_sent username=${player.accountName} index=${player.index}" }
            accounts.spawn(player, client)
            logger.info { "login_connect_spawn_complete username=${player.accountName} tile=${player.tile}" }
            AuditLog.event(player, "connected", player.tile)
        }
    }
}
