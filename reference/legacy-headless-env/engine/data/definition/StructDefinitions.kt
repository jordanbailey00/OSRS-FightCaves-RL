package sim.engine.data.definition

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.TestOnly
import sim.config.Config
import sim.cache.config.data.StructDefinition
import sim.cache.definition.data.ItemDefinition
import sim.engine.data.definition.ItemDefinitions.loaded
import sim.engine.timedLoad

/**
 * Also known as AttributeMaps in cs2 or rows
 */
object StructDefinitions : DefinitionsDecoder<StructDefinition> {

    override var definitions: Array<StructDefinition> = emptyArray()

    override var ids: Map<String, Int> = emptyMap()

    var loaded = false
        private set

    fun init(definitions: Array<StructDefinition>): StructDefinitions {
        this.definitions = definitions
        loaded = true
        return this
    }

    @TestOnly
    fun set(definitions: Array<StructDefinition>, ids: Map<String, Int>) {
        this.definitions = definitions
        this.ids = ids
        loaded = true
    }

    fun clear() {
        this.definitions = emptyArray()
        this.ids = emptyMap()
        loaded = false
    }

    override fun empty() = StructDefinition.EMPTY

    fun load(path: String): StructDefinitions {
        timedLoad("struct extra") {
            val ids = Object2IntOpenHashMap<String>(512, Hash.VERY_FAST_LOAD_FACTOR)
            Config.fileReader(path) {
                while (nextPair()) {
                    val stringId = key()
                    val id = int()
                    require(!ids.containsKey(stringId)) { "Duplicate struct id found '$stringId' at $path." }
                    ids[stringId] = id
                    definitions[id].stringId = stringId
                }
            }
            this.ids = ids
            ids.size
        }
        return this
    }
}
