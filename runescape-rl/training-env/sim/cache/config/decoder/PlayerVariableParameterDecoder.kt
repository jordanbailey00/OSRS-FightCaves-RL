package sim.cache.config.decoder

import sim.buffer.read.Reader
import sim.cache.Config.VARP
import sim.cache.config.ConfigDecoder
import sim.cache.config.data.PlayerVariableParameterDefinition

class PlayerVariableParameterDecoder : ConfigDecoder<PlayerVariableParameterDefinition>(VARP) {

    override fun create(size: Int) = Array(size) { PlayerVariableParameterDefinition(it) }

    override fun PlayerVariableParameterDefinition.read(opcode: Int, buffer: Reader) {
        if (opcode == 5) {
            type = buffer.readShort()
        }
    }
}
