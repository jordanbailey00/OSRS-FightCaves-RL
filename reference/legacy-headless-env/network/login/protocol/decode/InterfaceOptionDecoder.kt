package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.cache.definition.data.InterfaceDefinition
import sim.network.client.Instruction
import sim.network.client.instruction.InteractInterface
import sim.network.login.protocol.Decoder

class InterfaceOptionDecoder(private val index: Int) : Decoder(8) {

    override suspend fun decode(packet: Source): Instruction {
        val packed = packet.readInt()
        return InteractInterface(
            interfaceId = InterfaceDefinition.id(packed),
            componentId = InterfaceDefinition.componentId(packed),
            itemId = packet.readShort().toInt(),
            itemSlot = packet.readShort().toInt(),
            option = index,
        )
    }
}
