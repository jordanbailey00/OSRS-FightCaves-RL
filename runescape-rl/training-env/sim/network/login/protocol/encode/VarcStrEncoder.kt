package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.client.Client.Companion.BYTE
import sim.network.client.Client.Companion.SHORT
import sim.network.client.Client.Companion.string
import sim.network.login.Protocol.CLIENT_VARC_STR
import sim.network.login.Protocol.CLIENT_VARC_STR_LARGE
import sim.network.login.protocol.writeShortAdd
import sim.network.login.protocol.writeShortAddLittle
import sim.network.login.protocol.writeText

/**
 * Client variable; also known as "GlobalString"
 * @param id The config id
 * @param value The value to pass to the config
 */
fun Client.sendVarcStr(id: Int, value: String) {
    val size = 2 + string(value)
    if (size in 0..Byte.MAX_VALUE) {
        send(CLIENT_VARC_STR, size, BYTE) {
            writeText(value)
            writeShortAdd(id)
        }
    } else {
        send(CLIENT_VARC_STR_LARGE, size, SHORT) {
            writeShortAddLittle(id)
            writeText(value)
        }
    }
}
