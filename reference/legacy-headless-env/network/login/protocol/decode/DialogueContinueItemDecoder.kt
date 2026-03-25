package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractDialogueItem
import sim.network.login.protocol.Decoder

class DialogueContinueItemDecoder : Decoder(2) {

    override suspend fun decode(packet: Source): Instruction {
        val id = packet.readShort().toInt()
        return InteractDialogueItem(id)
    }
}
