package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.FriendAdd
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readString

class AddFriendDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction = FriendAdd(packet.readString())
}
