package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.login.Protocol.CLIENT_VARC
import sim.network.login.Protocol.CLIENT_VARC_LARGE
import sim.network.login.protocol.writeByte
import sim.network.login.protocol.writeIntLittle
import sim.network.login.protocol.writeShortAddLittle
import sim.network.login.protocol.writeShortLittle

/**
 * Client variable; also known as "ConfigGlobal"
 * @param id The config id
 * @param value The value to pass to the config
 */
fun Client.sendVarc(id: Int, value: Int) {
    if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) {
        send(CLIENT_VARC) {
            writeByte(value)
            writeShortAddLittle(id)
        }
    } else {
        send(CLIENT_VARC_LARGE) {
            writeIntLittle(value)
            writeShortLittle(id)
        }
    }
}
