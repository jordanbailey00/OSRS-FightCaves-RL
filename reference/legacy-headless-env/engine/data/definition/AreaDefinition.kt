package sim.engine.data.definition

import sim.cache.definition.Extra
import sim.types.Area
import sim.types.area.Rectangle

data class AreaDefinition(
    val name: String,
    val area: Area,
    val tags: Set<String> = emptySet(),
    override var stringId: String = name,
    override var extras: Map<String, Any>? = null,
) : Extra {
    companion object {
        val EMPTY = AreaDefinition("", Rectangle(0, 0, 0, 0), emptySet())
    }
}
