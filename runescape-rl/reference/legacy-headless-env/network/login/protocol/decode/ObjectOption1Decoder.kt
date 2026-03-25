package sim.network.login.protocol.decode

import io.ktor.utils.io.bits.*
import kotlinx.io.Source
import kotlinx.io.readUShort
import sim.network.client.Instruction
import sim.network.client.instruction.InteractObject
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanSubtract
import sim.network.login.protocol.readShortAddLittle

class ObjectOption1Decoder : Decoder(7) {

    override suspend fun decode(packet: Source): Instruction {
        val run = packet.readBooleanSubtract()
        val x = packet.readShortAddLittle()
        val y = packet.readUShort().reverseByteOrder().toInt()
        val objectId = packet.readUShort().toInt()
        return InteractObject(objectId, x, y, 1)
    }
}
