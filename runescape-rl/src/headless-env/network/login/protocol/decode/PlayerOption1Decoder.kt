package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractPlayer
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readByteInverse
import sim.network.login.protocol.readUnsignedShortAddLittle

class PlayerOption1Decoder : Decoder(3) {

    override suspend fun decode(packet: Source): Instruction {
        val index = packet.readUnsignedShortAddLittle()
        packet.readByteInverse()
        return InteractPlayer(index, 1)
    }
}
