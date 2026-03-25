package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.FriendDelete
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readString

class DeleteFriendDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction = FriendDelete(packet.readString())
}
