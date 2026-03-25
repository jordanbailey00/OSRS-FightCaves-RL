package sim.network.login.protocol.decode

import kotlinx.io.Source
import sim.network.client.Instruction
import sim.network.client.instruction.ReportAbuse
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readString

class ReportAbuseDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction {
        val name = packet.readString()
        val type = packet.readByte().toInt()
        val integer = packet.readByte().toInt()
        val string = packet.readString()
        return ReportAbuse(name, type, integer, string)
    }
}
