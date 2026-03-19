package sim.cache.definition.decoder

import com.github.michaelbull.logging.InlineLogger
import sim.buffer.read.Reader
import sim.cache.DefinitionDecoder
import sim.cache.Index.DEFAULTS
import sim.cache.definition.data.BodyDefinition

class BodyDecoder : DefinitionDecoder<BodyDefinition>(DEFAULTS) {

    val logger = InlineLogger()
    var definition: BodyDefinition? = null

    override fun create(size: Int) = Array(size) { BodyDefinition(it) }

    override fun getFile(id: Int) = 0

    override fun getArchive(id: Int) = 6

    override fun BodyDefinition.read(opcode: Int, buffer: Reader) {
        when (opcode) {
            1 -> disabledSlots = IntArray(buffer.readUnsignedByte()) { buffer.readUnsignedByte() }
            3 -> anInt4506 = buffer.readUnsignedByte()
            4 -> anInt4504 = buffer.readUnsignedByte()
            5 -> anIntArray4501 = IntArray(buffer.readUnsignedByte()) { buffer.readUnsignedByte() }
            6 -> anIntArray4507 = IntArray(buffer.readUnsignedByte()) { buffer.readUnsignedByte() }
        }
    }
}
