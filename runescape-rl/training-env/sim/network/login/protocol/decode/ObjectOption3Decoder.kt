package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractObject
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanAdd
import sim.network.login.protocol.readUnsignedShortAdd
import sim.network.login.protocol.readUnsignedShortAddLittle

class ObjectOption3Decoder : Decoder(7) {

    override suspend fun decode(packet: Source): Instruction {
        val y = packet.readUnsignedShortAdd()
        val objectId = packet.readUnsignedShortAddLittle()
        val x = packet.readShortLittleEndian().toInt()
        val run = packet.readBooleanAdd()
        return InteractObject(objectId, x, y, 3)
    }
}
