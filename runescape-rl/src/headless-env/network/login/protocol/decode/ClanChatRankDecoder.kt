package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.ClanChatRank
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readByteSubtract
import sim.network.login.protocol.readString

class ClanChatRankDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction {
        val rank = packet.readByteSubtract()
        val name = packet.readString()
        return ClanChatRank(name, rank)
    }
}
