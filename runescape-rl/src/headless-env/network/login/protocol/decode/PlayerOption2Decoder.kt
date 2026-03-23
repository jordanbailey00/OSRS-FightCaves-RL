package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractPlayer
import sim.network.login.protocol.Decoder

class PlayerOption2Decoder : Decoder(3) {

    override suspend fun decode(packet: Source): Instruction {
        packet.readByte()
        val index = packet.readShort().toInt()
        return InteractPlayer(index, 2)
    }
}
