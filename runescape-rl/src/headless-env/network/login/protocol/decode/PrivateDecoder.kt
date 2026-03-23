package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import kotlinx.io.readByteArray
import sim.cache.secure.Huffman
import sim.network.client.Instruction
import sim.network.client.instruction.ChatPrivate
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readSmart
import sim.network.login.protocol.readString

class PrivateDecoder(private val huffman: Huffman) : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction {
        val name = packet.readString()
        val message = huffman.decompress(length = packet.readSmart(), message = packet.readByteArray(packet.remaining.toInt())) ?: ""
        return ChatPrivate(name, message)
    }
}
