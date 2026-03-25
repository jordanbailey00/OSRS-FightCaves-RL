package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractPlayer
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readByteAdd
import sim.network.login.protocol.readUnsignedShortAdd

class PlayerOption7Decoder : Decoder(3) {

    override suspend fun decode(packet: Source): Instruction {
        val index = packet.readUnsignedShortAdd()
        packet.readByteAdd()
        return InteractPlayer(index, 7)
    }
}
