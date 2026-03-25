package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readUShort
import sim.network.client.Instruction
import sim.network.client.instruction.QuickChatPrivate
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readString

class PrivateQuickChatDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction {
        val name = packet.readString()
        val file = packet.readUShort().toInt()
        val data = packet.readByteArray(packet.remaining.toInt())
        return QuickChatPrivate(name, file, data)
    }

}
