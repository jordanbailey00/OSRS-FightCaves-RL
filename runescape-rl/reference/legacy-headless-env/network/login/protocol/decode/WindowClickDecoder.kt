package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.login.protocol.Decoder

class WindowClickDecoder : Decoder(6) {

    override suspend fun decode(packet: Source): Instruction? {
        val packed = packet.readShort().toInt()
        val position = packet.readInt()
        return null
    }
}
