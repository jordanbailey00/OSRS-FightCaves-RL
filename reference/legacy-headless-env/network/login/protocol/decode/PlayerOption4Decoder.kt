package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractPlayer
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readByteAdd

class PlayerOption4Decoder : Decoder(3) {

    override suspend fun decode(packet: Source): Instruction {
        val index = packet.readShort().toInt()
        packet.readByteAdd()
        return InteractPlayer(index, 4)
    }
}
