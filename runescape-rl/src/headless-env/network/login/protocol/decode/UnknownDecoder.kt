package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.login.protocol.Decoder

class UnknownDecoder : Decoder(2) {

    override suspend fun decode(packet: Source): Instruction? {
        val unknown = packet.readShort()
        return null
    }
}
