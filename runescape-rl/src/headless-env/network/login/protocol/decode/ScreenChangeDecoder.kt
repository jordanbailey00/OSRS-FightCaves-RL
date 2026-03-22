package sim.network.login.protocol.decode

import kotlinx.io.Source
import kotlinx.io.readUByte
import kotlinx.io.readUShort
import sim.network.client.Instruction
import sim.network.client.instruction.ChangeDisplayMode
import sim.network.login.protocol.Decoder

class ScreenChangeDecoder : Decoder(6) {

    override suspend fun decode(packet: Source): Instruction = ChangeDisplayMode(
        displayMode = packet.readUByte().toInt(),
        width = packet.readUShort().toInt(),
        height = packet.readUShort().toInt(),
        antialiasLevel = packet.readUByte().toInt(),
    )

}
