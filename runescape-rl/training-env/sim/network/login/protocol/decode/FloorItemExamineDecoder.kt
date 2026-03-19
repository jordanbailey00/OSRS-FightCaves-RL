package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.ExamineItem
import sim.network.login.protocol.Decoder

class FloorItemExamineDecoder : Decoder(2) {

    override suspend fun decode(packet: Source): Instruction {
        val itemId = packet.readShort().toInt()
        return ExamineItem(itemId)
    }
}
