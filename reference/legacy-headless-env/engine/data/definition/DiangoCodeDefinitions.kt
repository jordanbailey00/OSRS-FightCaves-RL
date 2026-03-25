package sim.engine.data.definition

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import sim.config.Config
import sim.engine.data.config.DiangoCodeDefinition
import sim.engine.entity.item.Item
import sim.engine.timedLoad

class DiangoCodeDefinitions {

    private lateinit var definitions: Map<String, DiangoCodeDefinition>

    fun get(code: String) = getOrNull(code) ?: DiangoCodeDefinition.EMPTY

    fun getOrNull(code: String) = definitions[code]

    fun load(path: String): DiangoCodeDefinitions {
        timedLoad("diango code definition") {
            val definitions = Object2ObjectOpenHashMap<String, DiangoCodeDefinition>(1, Hash.VERY_FAST_LOAD_FACTOR)
            Config.fileReader(path, 50) {
                while (nextSection()) {
                    val stringId = section()
                    var variable = ""
                    val items = ObjectArrayList<Item>(2)
                    while (nextPair()) {
                        val key = key()
                        when (key) {
                            "variable" -> variable = string()
                            "add" -> {
                                while (nextElement()) {
                                    val id = string()
                                    require(!ItemDefinitions.loaded || ItemDefinitions.contains(id)) { "Invalid diango item id: $id" }
                                    items.add(Item(id))
                                }
                            }
                        }
                        definitions[stringId] = DiangoCodeDefinition(variable, items)
                    }
                }
            }
            this.definitions = definitions
            definitions.size
        }
        return this
    }
}
