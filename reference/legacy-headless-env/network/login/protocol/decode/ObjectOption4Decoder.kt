package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractObject
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanAdd
import sim.network.login.protocol.readUnsignedShortAdd

class ObjectOption4Decoder : Decoder(7) {

    override suspend fun decode(packet: Source): Instruction {
        val run = packet.readBooleanAdd()
        val objectId = packet.readUnsignedShortAdd()
        val x = packet.readUnsignedShortAdd()
        val y = packet.readShortLittleEndian().toInt()
        return InteractObject(objectId, x, y, 4)
    }
}
