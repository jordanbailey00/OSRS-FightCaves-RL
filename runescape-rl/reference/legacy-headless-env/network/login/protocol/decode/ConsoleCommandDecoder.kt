package sim.network.login.protocol.decode

import kotlinx.io.Source
import kotlinx.io.readUByte
import sim.network.client.Instruction
import sim.network.client.instruction.ExecuteCommand
import sim.network.login.protocol.Decoder
import sim.network.login.protocol.readString

class ConsoleCommandDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction {
        val automatic = packet.readUByte().toInt() == 1
        val retainText = packet.readUByte().toInt() == 1
        val command = packet.readString()
        return ExecuteCommand(command, automatic, retainText)
    }
}
