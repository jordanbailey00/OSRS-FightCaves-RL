package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.cache.definition.data.InterfaceDefinition
import sim.network.client.Instruction
import sim.network.client.instruction.InteractInterfaceItem
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readShortAdd
import sim.network.login.protocol.readUnsignedIntInverseMiddle

class InterfaceOnInterfaceDecoder : Decoder(16) {

    override suspend fun decode(packet: Source): Instruction {
        val toPacked = packet.readInt()
        val fromPacked = packet.readUnsignedIntInverseMiddle()
        val fromSlot = packet.readShortAdd()
        val fromItem = packet.readShort().toInt()
        val toSlot = packet.readShortAdd()
        val toItem = packet.readShort().toInt()
        return InteractInterfaceItem(
            fromItem,
            toItem,
            fromSlot,
            toSlot,
            InterfaceDefinition.id(fromPacked),
            InterfaceDefinition.componentId(fromPacked),
            InterfaceDefinition.id(toPacked),
            InterfaceDefinition.componentId(toPacked),
        )
    }
}
