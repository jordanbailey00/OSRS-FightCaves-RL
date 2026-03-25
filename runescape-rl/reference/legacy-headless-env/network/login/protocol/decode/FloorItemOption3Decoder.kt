package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractFloorItem
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBoolean
import sim.network.login.protocol.readUnsignedShortAdd
import sim.network.login.protocol.readUnsignedShortAddLittle

class FloorItemOption3Decoder : Decoder(7) {

    override suspend fun decode(packet: Source): Instruction {
        val id = packet.readShort().toInt()
        val x = packet.readUnsignedShortAdd()
        val run = packet.readBoolean()
        val y = packet.readUnsignedShortAddLittle()
        return InteractFloorItem(id, x, y, 2)
    }
}
