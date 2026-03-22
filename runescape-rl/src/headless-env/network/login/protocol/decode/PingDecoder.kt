package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.login.protocol.Decoder

class PingDecoder : Decoder(0) {

    override suspend fun decode(packet: Source): Instruction? = null
}
