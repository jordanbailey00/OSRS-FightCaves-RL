package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractObject
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanAdd
import sim.network.login.protocol.readShortAddLittle
import sim.network.login.protocol.readUnsignedShortAdd

class ObjectOption5Decoder : Decoder(7) {

    override suspend fun decode(packet: Source): Instruction {
        val y = packet.readShortLittleEndian().toInt()
        val run = packet.readBooleanAdd()
        val x = packet.readShortAddLittle()
        val objectId = packet.readUnsignedShortAdd() and 0xffff
        return InteractObject(objectId, x, y, 5)
    }
}
