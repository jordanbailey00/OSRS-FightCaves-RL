package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readShortAdd

class APCoordinateDecoder : Decoder(12) {

    override suspend fun decode(packet: Source): Instruction? {
        val x = packet.readShortLittleEndian()
        val first = packet.readShortAdd()
        val third = packet.readShortLittleEndian()
        val fourth = packet.readInt()
        val y = packet.readShortAdd()
        return null
    }
}
