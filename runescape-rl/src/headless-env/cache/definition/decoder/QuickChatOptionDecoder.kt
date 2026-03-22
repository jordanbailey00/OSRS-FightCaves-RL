package sim.cache.definition.decoder

import sim.buffer.Unicode
import sim.buffer.read.ArrayReader
import sim.buffer.read.Reader
import sim.cache.Cache
import sim.cache.DefinitionDecoder
import sim.cache.Index.QUICK_CHAT_MENUS
import sim.cache.Index.QUICK_CHAT_MESSAGES
import sim.cache.definition.data.QuickChatOptionDefinition

class QuickChatOptionDecoder : DefinitionDecoder<QuickChatOptionDefinition>(QUICK_CHAT_MESSAGES) {

    override fun create(size: Int) = Array(size) { QuickChatOptionDefinition(it) }

    override fun getArchive(id: Int) = 0

    override fun size(cache: Cache): Int {
        val lastArchive = cache.lastArchiveId(index)
        val lastArchive2 = cache.lastArchiveId(QUICK_CHAT_MENUS)
        return lastArchive * 256 + cache.lastFileId(index, lastArchive) + (lastArchive2 * 256 + cache.lastFileId(index, lastArchive2))
    }

    override fun load(definitions: Array<QuickChatOptionDefinition>, cache: Cache, id: Int) {
        val archive = getArchive(id)
        val file = getFile(id)
        val data = (
            if (file <= 0x7fff) {
                cache.data(index, archive, file)
            } else {
                cache.data(QUICK_CHAT_MENUS, archive, file and 0x7fff)
            }
            ) ?: return
        read(definitions, id, ArrayReader(data))
    }

    override fun QuickChatOptionDefinition.read(opcode: Int, buffer: Reader) {
        when (opcode) {
            1 -> optionText = buffer.readString()
            2 -> {
                val length = buffer.readUnsignedByte()
                quickReplyOptions = IntArray(length)
                navigateChars = CharArray(length) { count ->
                    quickReplyOptions!![count] = buffer.readShort()
                    val b = buffer.readUnsignedByte()
                    if (b != 0) Unicode.byteToChar(b).toChar() else '\u0000'
                }
            }
            3 -> {
                val length = buffer.readUnsignedByte()
                dynamicData = IntArray(length)
                staticData = CharArray(length) { count ->
                    dynamicData!![count] = buffer.readShort()
                    val b = buffer.readUnsignedByte()
                    if (b != 0) Unicode.byteToChar(b).toChar() else '\u0000'
                }
            }
        }
    }

    override fun changeValues(definitions: Array<QuickChatOptionDefinition>, definition: QuickChatOptionDefinition) {
        if (definition.id >= 32768) {
            if (definition.dynamicData != null) {
                for (i in definition.dynamicData!!.indices) {
                    definition.dynamicData!![i] = definition.dynamicData!![i] or 32768
                }
            }
            if (definition.quickReplyOptions != null) {
                for (count in 0 until definition.quickReplyOptions!!.size) {
                    definition.quickReplyOptions!![count] = definition.quickReplyOptions!![count] or 32768
                }
            }
        }
    }
}
