package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractObject
import sim.network.login.protocol.*

class ObjectOption2Decoder : Decoder(7) {

    override suspend fun decode(packet: Source): Instruction {
        val y = packet.readShortAddLittle()
        val x = packet.readUnsignedShortAdd()
        val run = packet.readBooleanSubtract()
        val objectId = packet.readUnsignedShortAddLittle()
        return InteractObject(objectId, x, y, 2)
    }
}
