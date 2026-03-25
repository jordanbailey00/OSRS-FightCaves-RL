package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.login.protocol.Decoder

/**
 * Client has requested the lobby world list be refreshed
 */
class LobbyWorldListRefreshDecoder : Decoder(4) {

    override suspend fun decode(packet: Source): Instruction? {
        val latency = packet.readInt()
        return null
    }
}
