package sim.network.login.protocol.encode

import sim.buffer.write.ArrayWriter
import sim.network.client.Client
import sim.network.client.Client.Companion.SHORT
import sim.network.login.Protocol.PLAYER_UPDATING
import sim.network.login.protocol.writeBytes

fun Client.updatePlayers(
    changes: ArrayWriter,
    updates: ArrayWriter,
) = send(PLAYER_UPDATING, changes.position() + updates.position(), SHORT) {
    writeBytes(changes.toArray())
    writeBytes(updates.toArray())
}
