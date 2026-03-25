package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.cache.definition.data.InterfaceDefinition
import sim.network.client.Instruction
import sim.network.client.instruction.InteractDialogue
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readShortAdd
import sim.network.login.protocol.readUnsignedIntMiddle

class DialogueContinueDecoder : Decoder(6) {

    override suspend fun decode(packet: Source): Instruction {
        val button = packet.readShortAdd()
        val packed = packet.readUnsignedIntMiddle()
        return InteractDialogue(InterfaceDefinition.id(packed), InterfaceDefinition.componentId(packed), button)
    }
}
