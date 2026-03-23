package sim.cache.config.encoder

import sim.buffer.write.Writer
import sim.cache.config.ConfigEncoder
import sim.cache.config.data.InventoryDefinition

class InventoryEncoder : ConfigEncoder<InventoryDefinition>() {

    override fun Writer.encode(definition: InventoryDefinition) {
        if (definition.length != 0) {
            writeByte(2)
            writeShort(definition.length)
        }
        writeByte(0)
    }
}
