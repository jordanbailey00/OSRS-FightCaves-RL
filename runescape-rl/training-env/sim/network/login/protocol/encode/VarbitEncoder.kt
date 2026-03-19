package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.login.Protocol.CLIENT_VARBIT
import sim.network.login.Protocol.CLIENT_VARBIT_LARGE
import sim.network.login.protocol.writeByteAdd
import sim.network.login.protocol.writeIntInverseMiddle
import sim.network.login.protocol.writeShortAdd
import sim.network.login.protocol.writeShortLittle

/**
 * A variable bit; also known as "ConfigFile", known in the client as "clientvarpbit"
 * @param id The file id
 * @param value The value to pass to the config file
 */
fun Client.sendVarbit(id: Int, value: Int) {
    if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) {
        send(CLIENT_VARBIT) {
            writeByteAdd(value)
            writeShortLittle(id)
        }
    } else {
        send(CLIENT_VARBIT_LARGE) {
            writeShortAdd(id)
            writeIntInverseMiddle(value)
        }
    }
}
