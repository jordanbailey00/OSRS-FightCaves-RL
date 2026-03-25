package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.ClanChatJoin
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readString

class ClanChatJoinDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction = ClanChatJoin(packet.readString())
}
