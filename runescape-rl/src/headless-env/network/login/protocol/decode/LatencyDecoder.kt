package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.login.protocol.Decoder

class LatencyDecoder : Decoder(2) {

    override suspend fun decode(packet: Source): Instruction? {
        val value = packet.readShort().toInt()
        return null
    }
}
