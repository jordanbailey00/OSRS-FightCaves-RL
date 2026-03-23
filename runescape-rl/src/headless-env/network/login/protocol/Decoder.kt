package sim.network.login.protocol

import kotlinx.io.Source
import sim.network.client.Instruction

abstract class Decoder(val length: Int) {

    abstract suspend fun decode(packet: Source): Instruction?

    companion object {
        const val BYTE = -1
        const val SHORT = -2
    }
}
