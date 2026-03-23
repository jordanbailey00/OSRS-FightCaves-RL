package sim.network.login.protocol.decode

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import kotlinx.io.readUByte
import kotlinx.io.readUShort
import sim.network.client.Instruction
import sim.network.client.instruction.ContinueKey
import sim.network.login.protocol.Decoder

/**
 * key's pressed - Pair<Key, Time>
 */
class KeysPressedDecoder : Decoder(BYTE) {

    override suspend fun decode(packet: Source): Instruction? {
        var option: Int? = null
        while (packet.remaining > 0) {
            val key = packet.readUByte().toInt()
            val delta = packet.readUShort().toInt()
            if (key == 83) {
                option = -1
            } else if (key in 16..20) {
                option = key - 15
            }
        }
        if (option != null) {
            return ContinueKey(option)
        }
        return null
    }

}
