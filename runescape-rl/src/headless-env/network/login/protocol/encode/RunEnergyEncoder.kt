package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.login.Protocol.RUN_ENERGY
import sim.network.login.protocol.writeByte

/**
 * Sends run energy
 * @param energy The current energy value
 */
fun Client.sendRunEnergy(energy: Int) = send(RUN_ENERGY) {
    writeByte(energy)
}
