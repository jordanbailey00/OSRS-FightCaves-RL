package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InterfaceClosedInstruction
import sim.network.login.protocol.Decoder

class InterfaceClosedDecoder : Decoder(0) {

    override suspend fun decode(packet: Source): Instruction = InterfaceClosedInstruction
}
