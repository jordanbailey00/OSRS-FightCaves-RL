package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.cache.definition.data.InterfaceDefinition
import sim.network.client.Instruction
import sim.network.client.instruction.InteractInterfacePlayer
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readBooleanInverse
import sim.network.login.protocol.readShortAddLittle
import sim.network.login.protocol.readUnsignedIntInverseMiddle

class InterfaceOnPlayerDecoder : Decoder(11) {

    override suspend fun decode(packet: Source): Instruction {
        val slot = packet.readShortAddLittle()
        val index = packet.readShortLittleEndian().toInt()
        val itemId = packet.readShortLittleEndian().toInt()
        val packed = packet.readUnsignedIntInverseMiddle()
        val run = packet.readBooleanInverse()
        return InteractInterfacePlayer(
            index,
            InterfaceDefinition.id(packed),
            InterfaceDefinition.componentId(packed),
            itemId,
            slot,
        )
    }
}
