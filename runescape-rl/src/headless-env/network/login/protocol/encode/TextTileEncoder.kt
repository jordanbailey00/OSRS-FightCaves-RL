package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.client.Client.Companion.BYTE
import sim.network.client.Client.Companion.string
import sim.network.login.Protocol.TILE_TEXT
import sim.network.login.protocol.writeByte
import sim.network.login.protocol.writeMedium
import sim.network.login.protocol.writeShort
import sim.network.login.protocol.writeText

fun Client.tileText(
    tile: Int,
    duration: Int,
    height: Int,
    color: Int,
    text: String,
) = send(TILE_TEXT, 8 + string(text), BYTE) {
    writeByte(0)
    writeByte(tile)
    writeShort(duration)
    writeByte(height)
    writeMedium(color)
    writeText(text)
}
