package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.login.protocol.Decoder

class ToolkitPreferencesDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction? {
        packet.readByte()
        return null
    }
}
