package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.InteractNPC
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanAdd

class NPCOption4Decoder : Decoder(3) {

    override suspend fun decode(packet: Source): Instruction {
        val npcIndex = packet.readShortLittleEndian().toInt()
        val run = packet.readBooleanAdd()
        return InteractNPC(npcIndex, 4)
    }
}
