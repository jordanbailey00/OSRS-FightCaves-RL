package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.ClanChatKick
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readString

class ClanChatKickDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction = ClanChatKick(packet.readString())
}
