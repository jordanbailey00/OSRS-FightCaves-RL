package sim.engine.data.definition

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import sim.config.Config
import sim.cache.definition.data.GraphicDefinition
import sim.engine.timedLoad

class GraphicDefinitions(
    override var definitions: Array<GraphicDefinition>,
) : DefinitionsDecoder<GraphicDefinition> {

    override lateinit var ids: Map<String, Int>

    override fun empty() = GraphicDefinition.EMPTY

    fun load(paths: List<String>): GraphicDefinitions {
        timedLoad("graphic extra") {
            val ids = Object2IntOpenHashMap<String>(definitions.size, Hash.VERY_FAST_LOAD_FACTOR)
            for (path in paths) {
                Config.fileReader(path) {
                    while (nextSection()) {
                        val stringId = section()
                        var id = 0
                        val extras = Object2ObjectOpenHashMap<String, Any>(0)
                        while (nextPair()) {
                            when (val key = key()) {
                                "id" -> id = int()
                                "angle" -> throw IllegalArgumentException("Unknown key 'angle' use 'curve' instead. ${exception()}")
                                else -> extras[key] = value()
                            }
                        }
                        require(!ids.containsKey(stringId)) { "Duplicate graphics id found '$stringId' at $path." }
                        ids[stringId] = id
                        definitions[id].stringId = stringId
                        definitions[id].extras = extras.ifEmpty { null }
                    }
                }
            }
            this.ids = ids
            ids.size
        }
        return this
    }
}
