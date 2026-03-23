package sim.cache.config.decoder

import sim.buffer.read.Reader
import sim.cache.Config.VARC
import sim.cache.config.ConfigDecoder
import sim.cache.config.data.ClientVariableParameterDefinition

class ClientVariableParameterDecoder : ConfigDecoder<ClientVariableParameterDefinition>(VARC) {

    override fun create(size: Int) = Array(size) { ClientVariableParameterDefinition(it) }

    override fun ClientVariableParameterDefinition.read(opcode: Int, buffer: Reader) {
        if (opcode == 1) {
            aChar3210 = buffer.readChar().toChar()
        } else if (opcode == 2) {
            anInt3208 = 0
        }
    }
}
