package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.login.protocol.Decoder

class RegionLoadingDecoder : Decoder(4) {

    override suspend fun decode(packet: Source): Instruction? {
        packet.readInt() // 1057001181
        return null
    }
}
