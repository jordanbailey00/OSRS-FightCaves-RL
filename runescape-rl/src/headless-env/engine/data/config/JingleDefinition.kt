package sim.engine.data.config

import sim.cache.Definition
import sim.cache.definition.Extra

data class JingleDefinition(
    override var id: Int,
    override var stringId: String = "",
    override var extras: Map<String, Any>? = null,
) : Definition,
    Extra {
    companion object {
        val EMPTY = JingleDefinition(-1)
    }
}
