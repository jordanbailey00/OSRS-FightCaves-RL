package content.entity.player.modal

import sim.cache.config.data.StructDefinition
import sim.engine.data.definition.StructDefinitions

object CharacterStyle {
    private const val STRUCT = 1048

    fun onStyle(top: Int, block: (StructDefinition) -> Unit) {
        for (i in 0 until 64) {
            val style = StructDefinitions.get(STRUCT + i)
            if (style.get<Int>("character_style_top") == top) {
                block.invoke(style)
                break
            }
        }
    }
}
