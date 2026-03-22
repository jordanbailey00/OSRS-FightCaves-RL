package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.instruction.WorldMapClick
import sim.network.login.protocol.Decoder

class WorldMapClickDecoder : Decoder(4) {

    override suspend fun decode(packet: Source) = WorldMapClick(packet.readInt())
}
