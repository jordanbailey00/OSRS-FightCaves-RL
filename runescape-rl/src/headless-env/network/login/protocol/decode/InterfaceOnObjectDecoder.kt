package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import kotlinx.io.readIntLe
import sim.cache.definition.data.InterfaceDefinition
import sim.network.client.Instruction
import sim.network.client.instruction.InteractInterfaceObject
import sim.network.login.protocol.*

class InterfaceOnObjectDecoder : Decoder(15) {

    override suspend fun decode(packet: Source): Instruction {
        val item = packet.readShort().toInt()
        val x = packet.readShortAddLittle()
        val packed = packet.readIntLe()
        val y = packet.readUnsignedShortAdd()
        val run = packet.readBooleanSubtract()
        val index = packet.readShortLittleEndian().toInt()
        val objectId = packet.readUnsignedShortLittle()
        return InteractInterfaceObject(
            objectId,
            x,
            y,
            InterfaceDefinition.id(packed),
            InterfaceDefinition.componentId(packed),
            item,
            index,
        )
    }
}
