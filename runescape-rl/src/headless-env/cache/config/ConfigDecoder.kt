package sim.cache.config

import sim.buffer.read.Reader
import sim.cache.Cache
import sim.cache.Definition
import sim.cache.DefinitionDecoder
import sim.cache.Index

abstract class ConfigDecoder<T : Definition>(internal val archive: Int) : DefinitionDecoder<T>(Index.CONFIGS) {

    override fun getArchive(id: Int) = archive

    override fun readId(reader: Reader): Int = reader.readShort()

    override fun size(cache: Cache): Int = cache.lastFileId(Index.CONFIGS, archive)
}
