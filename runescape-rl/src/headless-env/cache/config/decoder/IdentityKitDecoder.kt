package sim.cache.config.decoder

import sim.buffer.read.Reader
import sim.cache.Config.IDENTITY_KIT
import sim.cache.config.ConfigDecoder
import sim.cache.config.data.IdentityKitDefinition

class IdentityKitDecoder : ConfigDecoder<IdentityKitDefinition>(IDENTITY_KIT) {

    override fun create(size: Int) = Array(size) { IdentityKitDefinition(it) }

    override fun IdentityKitDefinition.read(opcode: Int, buffer: Reader) {
        when (opcode) {
            1 -> bodyPartId = buffer.readUnsignedByte()
            2 -> {
                val length = buffer.readUnsignedByte()
                modelIds = IntArray(length) { buffer.readUnsignedShort() }
            }
            40 -> readColours(buffer)
            41 -> readTextures(buffer)
            in 60..69 -> headModels[opcode - 60] = buffer.readUnsignedShort()
        }
    }
}
