package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractPlayer
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readByteSubtract

class PlayerOption3Decoder : Decoder(3) {

    override suspend fun decode(packet: Source): Instruction {
        packet.readByteSubtract()
        val index = packet.readShortLittleEndian().toInt()
        return InteractPlayer(index, 3)
    }
}
