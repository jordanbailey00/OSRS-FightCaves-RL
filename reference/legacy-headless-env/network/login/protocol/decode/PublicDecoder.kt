package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readUByte
import sim.cache.secure.Huffman
import sim.network.client.Instruction
import sim.network.client.instruction.ChatPublic
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readSmart

class PublicDecoder(private val huffman: Huffman) : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction {
        val effects = (packet.readUByte().toInt() shl 8) or packet.readUByte().toInt()
        val message = huffman.decompress(length = packet.readSmart(), message = packet.readByteArray(packet.remaining.toInt())) ?: ""
        return ChatPublic(message, effects)
    }
}
