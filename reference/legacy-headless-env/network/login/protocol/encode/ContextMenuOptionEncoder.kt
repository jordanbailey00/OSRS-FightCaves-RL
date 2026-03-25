package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.client.Client.Companion.BYTE
import sim.network.client.Client.Companion.string
import sim.network.login.Protocol.PLAYER_OPTION
import sim.network.login.protocol.writeByteAdd
import sim.network.login.protocol.writeByteSubtract
import sim.network.login.protocol.writeShortAddLittle
import sim.network.login.protocol.writeText

/**
 * Sends a player right click option
 * @param option The option
 * @param slot The index of the option
 * @param top Whether it should be forced to the top?
 * @param cursor Unknown value
 */
fun Client.contextMenuOption(
    option: String?,
    slot: Int,
    top: Boolean,
    cursor: Int = -1,
) = send(PLAYER_OPTION, 4 + string(option), BYTE) {
    writeShortAddLittle(cursor)
    writeText(option)
    writeByteSubtract(slot)
    writeByteAdd(top)
}
