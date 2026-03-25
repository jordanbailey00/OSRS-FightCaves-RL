package sim.cache.config.decoder

import sim.buffer.read.Reader
import sim.cache.Config.STRUCTS
import sim.cache.config.ConfigDecoder
import sim.cache.config.data.StructDefinition
import sim.cache.definition.Parameters

class StructDecoder(
    private val parameters: Parameters = Parameters.EMPTY,
) : ConfigDecoder<StructDefinition>(STRUCTS) {

    override fun create(size: Int) = Array(size) { StructDefinition(it) }

    override fun StructDefinition.read(opcode: Int, buffer: Reader) {
        if (opcode == 249) {
            readParameters(buffer, parameters)
        }
    }
}
