package sim.network.file.type

import io.ktor.utils.io.*
import sim.cache.Cache
import sim.network.file.FileProvider
import sim.network.file.FileProvider.Companion.LARGEST_BLOCK
import sim.network.file.FileProvider.Companion.OFFSET
import sim.network.file.FileProvider.Companion.SEPARATOR
import sim.network.file.FileProvider.Companion.SPLIT
import sim.network.file.FileProvider.Companion.getInt
import sim.network.login.protocol.writeByte
import kotlin.math.min

/**
 * Reads sectors directly from the [cache]
 * Average read speeds, low memory usage
 */
class CacheFileProvider(private val cache: Cache) : FileProvider {

    override fun data(index: Int, archive: Int): ByteArray? {
        if (index == 255 && archive == 255) {
            return cache.versionTable
        }
        return cache.sector(index, archive)
    }

    override suspend fun encode(write: ByteWriteChannel, data: ByteArray) {
        val compression = data[0].toInt()
        val size = getInt(data[1], data[2], data[3], data[4]) + if (compression != 0) 8 else 4
        var length = min(size, LARGEST_BLOCK)
        write.writeFully(data, OFFSET, OFFSET + length)
        var written = length
        while (written < size) {
            write.writeByte(SEPARATOR)
            length = if (size - written < SPLIT) size - written else SPLIT - 1
            write.writeFully(data, written + OFFSET, written + OFFSET + length)
            written += length
        }
    }
}
