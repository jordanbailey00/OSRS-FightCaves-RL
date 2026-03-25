package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.Walk
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanAdd
import sim.network.login.protocol.readUnsignedShortAdd

class WalkMapDecoder : Decoder(5) {

    override suspend fun decode(packet: Source): Instruction {
        val y = packet.readShortLittleEndian().toInt()
        val running = packet.readBooleanAdd()
        val x = packet.readUnsignedShortAdd()
        return Walk(x, y)
    }
}
