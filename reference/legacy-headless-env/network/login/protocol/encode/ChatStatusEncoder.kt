package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.login.Protocol.PRIVATE_STATUS
import sim.network.login.Protocol.PUBLIC_STATUS
import sim.network.login.protocol.writeByte
import sim.network.login.protocol.writeByteAdd
import sim.network.login.protocol.writeByteSubtract

/**
 * @param public (0 = on, 1 = friends, 2 = off, 3 = hide)
 * @param trade (0 = on, 1 = friends, 2 = off)
 */
fun Client.sendPublicStatus(public: Int, trade: Int) {
    send(PUBLIC_STATUS, 2, Client.FIXED) {
        writeByteSubtract(public)
        writeByteAdd(trade)
    }
}

/**
 * @param status (0 = on, 1 = friends, 2 = off)
 */
fun Client.sendPrivateStatus(status: Int) {
    send(PRIVATE_STATUS, 1, Client.FIXED) {
        writeByte(status)
    }
}
