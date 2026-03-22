package sim.cache.config.decoder

import sim.buffer.read.Reader
import sim.cache.Config.INVENTORIES
import sim.cache.config.ConfigDecoder
import sim.cache.config.data.InventoryDefinition

class InventoryDecoder : ConfigDecoder<InventoryDefinition>(INVENTORIES) {

    override fun create(size: Int) = Array(size) { InventoryDefinition(it) }

    override fun InventoryDefinition.read(opcode: Int, buffer: Reader) {
        when (opcode) {
            2 -> length = buffer.readUnsignedShort()
            4 -> {
                val size = buffer.readUnsignedByte()
                ids = IntArray(size)
                amounts = IntArray(size)
                for (i in 0 until size) {
                    ids!![i] = buffer.readUnsignedShort()
                    amounts!![i] = buffer.readUnsignedShort()
                }
            }
        }
    }
}
