package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.cache.definition.data.InterfaceDefinition
import sim.network.client.Instruction
import sim.network.client.instruction.InteractInterfaceNPC
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanAdd
import sim.network.login.protocol.readShortAddLittle
import sim.network.login.protocol.readUnsignedShortAdd

class InterfaceOnNpcDecoder : Decoder(11) {

    override suspend fun decode(packet: Source): Instruction {
        val slot = packet.readShortAddLittle()
        val packed = packet.readInt()
        val npc = packet.readShortLittleEndian().toInt()
        val run = packet.readBooleanAdd()
        val itemId = packet.readUnsignedShortAdd()
        return InteractInterfaceNPC(
            npc,
            InterfaceDefinition.id(packed),
            InterfaceDefinition.componentId(packed),
            itemId,
            slot,
        )
    }
}
