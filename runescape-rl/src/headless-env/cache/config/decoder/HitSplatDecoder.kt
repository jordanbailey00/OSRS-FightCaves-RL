package sim.cache.config.decoder

import sim.buffer.read.Reader
import sim.cache.Config.HIT_SPLATS
import sim.cache.config.ConfigDecoder
import sim.cache.config.data.HitSplatDefinition

class HitSplatDecoder : ConfigDecoder<HitSplatDefinition>(HIT_SPLATS) {

    override fun create(size: Int) = Array(size) { HitSplatDefinition(it) }

    override fun HitSplatDefinition.read(opcode: Int, buffer: Reader) {
        when (opcode) {
            1 -> font = buffer.readShort()
            2 -> textColour = buffer.readUnsignedMedium()
            3 -> icon = buffer.readShort()
            4 -> left = buffer.readShort()
            5 -> middle = buffer.readShort()
            6 -> right = buffer.readShort()
            7 -> offsetX = buffer.readUnsignedShort()
            8 -> amount = buffer.readString()
            9 -> duration = buffer.readShort()
            10 -> offsetY = buffer.readUnsignedShort()
            11 -> fade = 0
            12 -> comparisonType = buffer.readUnsignedByte()
            13 -> anInt3214 = buffer.readUnsignedShort()
            14 -> fade = buffer.readShort()
        }
    }
}
