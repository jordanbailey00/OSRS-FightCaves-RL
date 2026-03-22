package sim.network.login

import kotlinx.coroutines.channels.SendChannel
import sim.network.client.Client
import sim.network.client.Instruction

/**
 * Loads and checks existing accounts
 */
interface AccountLoader {
    fun exists(username: String): Boolean

    fun password(username: String): String?

    suspend fun load(client: Client, username: String, passwordHash: String, displayMode: Int): SendChannel<Instruction>?
}
