package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readString

class HyperlinkDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction? {
        val name = packet.readString()
        val script = packet.readString()
        val third = packet.readByte()
        return null
    }
}
