package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readShortAddLittle

class SecondaryTeleportDecoder : Decoder(4) {

    override suspend fun decode(packet: Source): Instruction? {
        val x = packet.readShortAddLittle()
        val y = packet.readShortLittleEndian().toInt()
        return null
    }
}
