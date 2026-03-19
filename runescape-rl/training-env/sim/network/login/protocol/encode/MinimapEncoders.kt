package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.login.Protocol.MINIMAP_STATE
import sim.network.login.protocol.writeByte

fun Client.sendMinimapState(state: Int) {
    send(MINIMAP_STATE) {
        writeByte(state)
    }
}
