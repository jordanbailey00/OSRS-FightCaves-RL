package sim.network.login.protocol.encode

import io.ktor.utils.io.*
import sim.network.client.Client
import sim.network.client.Client.Companion.string
import sim.network.login.Protocol.UPDATE_FRIENDS
import sim.network.login.protocol.writeByte
import sim.network.login.protocol.writeShort
import sim.network.login.protocol.writeText

data class Friend(
    val name: String,
    val previousName: String,
    val rank: Int = 0,
    val renamed: Boolean = false,
    val world: Int = 0,
    val worldName: String = "",
    val gameQuickChat: Boolean = true,
)

fun Client.sendFriendsList(friends: List<Friend>) {
    send(UPDATE_FRIENDS, friends.sumOf { count(it) }, Client.SHORT) {
        for (friend in friends) {
            writeFriend(friend)
        }
    }
}

private fun count(friend: Friend) = 4 + string(friend.name) + string(friend.previousName) + if (friend.world > 0) string(friend.worldName) + 1 else 0

private suspend fun ByteWriteChannel.writeFriend(friend: Friend) {
    writeByte(friend.renamed)
    writeText(friend.name)
    writeText(friend.previousName)
    writeShort(friend.world)
    writeByte(friend.rank)
    if (friend.world > 0) {
        writeText(friend.worldName)
        writeByte(friend.gameQuickChat)
    }
}
