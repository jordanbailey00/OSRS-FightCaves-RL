package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.Walk
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanAdd
import sim.network.login.protocol.readUnsignedShortAdd

class WalkMiniMapDecoder : Decoder(18) {

    override suspend fun decode(packet: Source): Instruction {
        val y = packet.readShortLittleEndian().toInt()
        val running = packet.readBooleanAdd()
        val x = packet.readUnsignedShortAdd()
        packet.readByte() // -1
        packet.readByte() // -1
        packet.readShort() // Rotation?
        packet.readByte() // 57
        val minimapRotation = packet.readByte()
        val minimapZoom = packet.readByte()
        packet.readByte() // 89
        packet.readShort() // X in region?
        packet.readShort() // Y in region?
        packet.readByte() // 63
        return Walk(x, y, minimap = true)
    }
}
