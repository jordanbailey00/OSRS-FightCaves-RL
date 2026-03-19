package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.cache.definition.data.InterfaceDefinition
import sim.network.client.Instruction
import sim.network.client.instruction.MoveInventoryItem
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readShortAddLittle
import sim.network.login.protocol.readUnsignedIntMiddle

class InterfaceSwitchComponentsDecoder : Decoder(16) {

    override suspend fun decode(packet: Source): Instruction {
        val fromPacked = packet.readInt()
        val toSlot = packet.readShortLittleEndian().toInt()
        val toPacked = packet.readUnsignedIntMiddle()
        val toItemId = packet.readShort().toInt()
        val fromSlot = packet.readShortAddLittle()
        val fromItemId = packet.readShortAddLittle()
        return MoveInventoryItem(
            fromId = InterfaceDefinition.id(fromPacked),
            fromComponentId = InterfaceDefinition.componentId(fromPacked),
            fromItemId = fromItemId,
            fromSlot = fromSlot,
            toId = InterfaceDefinition.id(toPacked),
            toComponentId = InterfaceDefinition.componentId(toPacked),
            toItemId = toItemId,
            toSlot = toSlot,
        )
    }
}
