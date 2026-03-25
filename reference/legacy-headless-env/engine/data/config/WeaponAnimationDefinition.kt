package sim.engine.data.config

import sim.cache.Definition
import sim.cache.definition.Extra

data class WeaponAnimationDefinition(
    override var id: Int = -1,
    val attackTypes: Map<String, String> = emptyMap(),
    override var stringId: String = "",
    override var extras: Map<String, Any>? = null,
) : Definition,
    Extra {

    companion object {
        val EMPTY = WeaponAnimationDefinition()

        @Suppress("UNCHECKED_CAST")
        fun fromMap(stringId: String, map: Map<String, Any>) = WeaponAnimationDefinition(
            attackTypes = (map as? Map<String, String>) ?: EMPTY.attackTypes,
            stringId = stringId,
        )
    }
}
