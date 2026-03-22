package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.EnterInt
import sim.network.login.protocol.Decoder

class IntegerEntryDecoder : Decoder(4) {

    override suspend fun decode(packet: Source): Instruction {
        val integer = packet.readInt()
        return EnterInt(integer)
    }
}
