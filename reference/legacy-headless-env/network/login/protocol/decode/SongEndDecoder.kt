package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.SongEnd
import sim.network.login.protocol.Decoder

class SongEndDecoder : Decoder(4) {

    override suspend fun decode(packet: Source): Instruction {
        val songIndex = packet.readInt()
        return SongEnd(
            songIndex,
        )
    }
}
