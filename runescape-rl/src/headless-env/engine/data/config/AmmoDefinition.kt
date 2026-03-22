package sim.engine.data.config

import sim.cache.Definition
import sim.cache.definition.Extra

data class AmmoDefinition(
    override var id: Int,
    val items: Set<String> = setOf(),
    override var stringId: String = "",
    override var extras: Map<String, Any>? = null,
) : Definition,
    Extra {
    companion object {
        val EMPTY = AmmoDefinition(-1)
    }
}
