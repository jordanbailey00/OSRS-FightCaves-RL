package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.login.Protocol
import sim.network.login.protocol.writeByte
import sim.network.login.protocol.writeByteAdd
import sim.network.login.protocol.writeIntInverseMiddle
import sim.network.login.protocol.writeShort
import sim.network.login.protocol.writeShortAddLittle

/**
 * Show animation of an object for a single client
 * @param tile 30 bit location hash
 * @param animation Animation id
 * @param type Object type
 * @param rotation Object rotation
 */
fun Client.animateObject(
    tile: Int,
    animation: Int,
    type: Int,
    rotation: Int,
) = send(Protocol.OBJECT_ANIMATION) {
    writeShortAddLittle(animation)
    writeByteAdd((type shl 2) or rotation)
    writeIntInverseMiddle(tile)
}

/**
 * Preloads a object model
 */
fun Client.preloadObject(
    id: Int,
    modelType: Int,
) = send(Protocol.OBJECT_PRE_FETCH) {
    writeShort(id)
    writeByte(modelType)
}
