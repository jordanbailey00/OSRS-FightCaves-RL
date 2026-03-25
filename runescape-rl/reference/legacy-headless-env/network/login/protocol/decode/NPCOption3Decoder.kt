package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractNPC
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBoolean

class NPCOption3Decoder : Decoder(3) {

    override suspend fun decode(packet: Source): Instruction {
        val npcIndex = packet.readShort().toInt()
        val run = packet.readBoolean()
        return InteractNPC(npcIndex, 3)
    }
}
