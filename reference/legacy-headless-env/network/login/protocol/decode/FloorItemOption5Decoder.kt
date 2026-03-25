package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractFloorItem
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanInverse
import sim.network.login.protocol.readShortAdd

class FloorItemOption5Decoder : Decoder(7) {

    override suspend fun decode(packet: Source): Instruction {
        val y = packet.readShort().toInt()
        val x = packet.readShortAdd()
        val run = packet.readBooleanInverse()
        val id = packet.readShortAdd()
        return InteractFloorItem(id, x, y, 4)
    }
}
