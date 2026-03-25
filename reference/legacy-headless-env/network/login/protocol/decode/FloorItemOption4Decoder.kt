package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractFloorItem
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanSubtract
import sim.network.login.protocol.readUnsignedShortAdd

class FloorItemOption4Decoder : Decoder(7) {

    override suspend fun decode(packet: Source): Instruction {
        val run = packet.readBooleanSubtract()
        val x = packet.readUnsignedShortAdd()
        val y = packet.readShortLittleEndian().toInt()
        val id = packet.readShort().toInt()
        return InteractFloorItem(id, x, y, 3)
    }
}
