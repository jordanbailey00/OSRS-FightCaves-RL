package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.login.Protocol.PLAYER_WEIGHT
import sim.network.login.protocol.writeShort

/**
 * Updates player weight for equipment screen
 */
fun Client.weight(
    weight: Int,
) = send(PLAYER_WEIGHT) {
    writeShort(weight)
}
