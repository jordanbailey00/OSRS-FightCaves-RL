package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.ExamineNpc
import sim.network.login.protocol.Decoder

class NPCExamineDecoder : Decoder(2) {

    override suspend fun decode(packet: Source): Instruction {
        val npcId = packet.readShort().toInt()
        return ExamineNpc(npcId)
    }
}
