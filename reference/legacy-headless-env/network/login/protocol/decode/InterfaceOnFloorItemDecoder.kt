package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.cache.definition.data.InterfaceDefinition
import sim.network.client.Instruction
import sim.network.client.instruction.InteractInterfaceFloorItem
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBoolean
import sim.network.login.protocol.readUnsignedIntMiddle
import sim.network.login.protocol.readUnsignedShortAdd

class InterfaceOnFloorItemDecoder : Decoder(15) {

    override suspend fun decode(packet: Source): Instruction {
        val x = packet.readShortLittleEndian().toInt()
        val floorItem = packet.readUnsignedShortAdd()
        val itemSlot = packet.readShortLittleEndian().toInt()
        val y = packet.readShort().toInt()
        val run = packet.readBoolean()
        val item = packet.readUnsignedShortAdd()
        val packed = packet.readUnsignedIntMiddle()
        return InteractInterfaceFloorItem(
            floorItem,
            x,
            y,
            InterfaceDefinition.id(packed),
            InterfaceDefinition.componentId(packed),
            item,
            itemSlot,
        )
    }
}
