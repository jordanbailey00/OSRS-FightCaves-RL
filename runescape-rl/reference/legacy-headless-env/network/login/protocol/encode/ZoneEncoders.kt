package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.login.Protocol
import sim.network.login.Protocol.CLEAR_ZONE
import sim.network.login.protocol.writeByteAdd
import sim.network.login.protocol.writeByteInverse

/**
 * @param xOffset The zone x coordinate relative to viewport
 * @param yOffset The zone y coordinate relative to viewport
 * @param level The zones level
 */
fun Client.clearZone(
    xOffset: Int,
    yOffset: Int,
    level: Int,
) = send(CLEAR_ZONE) {
    writeByteAdd(level)
    writeByteInverse(yOffset)
    writeByteInverse(xOffset)
}

/**
 * @param xOffset The zone x coordinate relative to viewport
 * @param yOffset The zone y coordinate relative to viewport
 * @param level The zones level
 */
fun Client.updateZone(
    xOffset: Int,
    yOffset: Int,
    level: Int,
) = send(Protocol.UPDATE_ZONE) {
    writeByteInverse(xOffset)
    writeByteAdd(level)
    writeByteAdd(yOffset)
}
