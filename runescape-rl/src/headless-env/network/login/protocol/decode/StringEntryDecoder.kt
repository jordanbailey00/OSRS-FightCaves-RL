package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.EnterString
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readString

class StringEntryDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction = EnterString(packet.readString())
}
