package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractFloorItem
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanInverse
import sim.network.login.protocol.readShortAdd
import sim.network.login.protocol.readUnsignedShortAdd

class FloorItemOption2Decoder : Decoder(7) {

    override suspend fun decode(packet: Source): Instruction {
        val y = packet.readUnsignedShortAdd()
        val id = packet.readShortAdd()
        val x = packet.readShortLittleEndian().toInt()
        val run = packet.readBooleanInverse()
        return InteractFloorItem(id, x, y, 1)
    }
}
