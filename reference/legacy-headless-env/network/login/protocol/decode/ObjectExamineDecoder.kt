package sim.network.login.protocol.decode

import kotlinx.io.Source
import kotlinx.io.readUShort
import sim.network.client.Instruction
import sim.network.client.instruction.ExamineObject
import sim.network.login.protocol.Decoder

class ObjectExamineDecoder : Decoder(2) {

    override suspend fun decode(packet: Source): Instruction {
        val objectId = packet.readUShort().toInt()
        return ExamineObject(objectId)
    }

}
