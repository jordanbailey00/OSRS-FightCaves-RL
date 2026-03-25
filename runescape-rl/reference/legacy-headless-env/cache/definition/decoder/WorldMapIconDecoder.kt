package sim.cache.definition.decoder

import sim.buffer.read.ArrayReader
import sim.buffer.read.Reader
import sim.cache.Cache
import sim.cache.DefinitionDecoder
import sim.cache.Index.WORLD_MAP
import sim.cache.definition.data.WorldMapIcon
import sim.cache.definition.data.WorldMapIconDefinition

class WorldMapIconDecoder : DefinitionDecoder<WorldMapIconDefinition>(WORLD_MAP) {

    private var archive = -1
    private var aBoolean1313 = false

    override fun getArchive(id: Int) = archive

    override fun size(cache: Cache): Int = cache.lastFileId(index, archive)

    override fun create(size: Int) = Array(size) { WorldMapIconDefinition(it) }

    override fun load(definitions: Array<WorldMapIconDefinition>, cache: Cache, id: Int) {
        val archive = getArchive(id)
        var length = cache.fileCount(index, archive)
        var counter = 0
        var index = 0
        if (length > 0) {
            val definition = definitions[id]
            val icons = mutableListOf<WorldMapIcon>()
            while (length > counter) {
                val data = cache.data(this.index, archive, index++) ?: continue
                val buffer = ArrayReader(data)
                val position = buffer.readInt()
                val iconId = buffer.readShort()
                val skip = buffer.readUnsignedByte()
                if (aBoolean1313 && skip == 1) {
                    length--
                } else {
                    counter++
                    icons.add(WorldMapIcon(iconId, position))
                }
            }
            definition.icons = icons.toTypedArray()
        }
    }

    override fun WorldMapIconDefinition.read(opcode: Int, buffer: Reader) {
    }
}
