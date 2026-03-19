package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.FinishRegionLoad
import sim.network.login.protocol.Decoder

class RegionLoadedDecoder : Decoder(0) {

    override suspend fun decode(packet: Source): Instruction = FinishRegionLoad
}
