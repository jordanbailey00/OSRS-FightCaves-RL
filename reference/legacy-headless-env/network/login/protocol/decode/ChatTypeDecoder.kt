package sim.network.login.protocol.decode

import kotlinx.io.Source
import kotlinx.io.readUByte
import sim.network.client.Instruction
import sim.network.client.instruction.ChatTypeChange
import sim.network.login.protocol.Decoder

/**
 * Notified the type of message before a message is sent
 * The type of message sent (0 = public, 1 = clan chat)
 */
class ChatTypeDecoder : Decoder(1) {
    override suspend fun decode(packet: Source): Instruction = ChatTypeChange(packet.readUByte().toInt())
}
