package content.skill.firemaking

import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.EnumDefinitions
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level.has
import sim.engine.inv.inventory
import sim.engine.inv.replace

class LightSource : Script {

    init {
        val unlitSources = buildString {
            append("oil_lamp_oil,")
            append("candle_lantern_white,")
            append("candle_lantern_black,")
            append("oil_lantern_oil,")
            append("bullseye_lantern_oil,")
            append("sapphire_lantern_oil,")
            append("mining_helmet,")
            append("emerald_lantern,")
            append("white_candle,")
            append("black_candle,")
            append("unlit_torch")
        }
        itemOnItem("tinderbox*", unlitSources) { _, toItem ->
            val lit = EnumDefinitions.stringOrNull("light_source_lit", toItem.id) ?: return@itemOnItem
            val level = EnumDefinitions.int("light_source_level", lit)
            if (!has(Skill.Firemaking, level, true)) {
                return@itemOnItem
            }
            if (!inventory.replace(toItem.id, lit)) {
                return@itemOnItem
            }
            val litItem = determineLightSource(lit)
            message("You light the $litItem", ChatType.Game)
        }

        itemOption("Extinguish") { (item) ->
            val extinguished = EnumDefinitions.stringOrNull("light_source_extinguish", item.id) ?: return@itemOption
            if (!inventory.replace(item.id, extinguished)) {
                return@itemOption
            }
            message("You extinguish the flame.", ChatType.Game)
        }
    }

    fun determineLightSource(itemName: String): String = when {
        itemName.contains("lantern", ignoreCase = true) -> "lantern."
        itemName.contains("candle", ignoreCase = true) -> "candle."
        else -> "null"
    }
}
