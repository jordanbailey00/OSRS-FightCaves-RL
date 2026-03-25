package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBoolean

class WindowFocusDecoder : Decoder(1) {

    override suspend fun decode(packet: Source): Instruction? {
        val focused = packet.readBoolean()
        return null
    }
}
