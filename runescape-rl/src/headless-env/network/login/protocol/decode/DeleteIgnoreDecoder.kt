package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.IgnoreDelete
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readString

class DeleteIgnoreDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction = IgnoreDelete(packet.readString())
}
