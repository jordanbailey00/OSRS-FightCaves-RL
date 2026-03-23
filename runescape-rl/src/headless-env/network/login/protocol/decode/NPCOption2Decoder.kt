package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractNPC
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanAdd
import sim.network.login.protocol.readShortAddLittle

class NPCOption2Decoder : Decoder(3) {

    override suspend fun decode(packet: Source): Instruction {
        val npcIndex = packet.readShortAddLittle()
        val run = packet.readBooleanAdd()
        return InteractNPC(npcIndex, 2)
    }
}
