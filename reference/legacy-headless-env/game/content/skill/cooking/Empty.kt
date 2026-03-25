package content.skill.cooking

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.inv.inventory
import sim.engine.inv.replace

class Empty : Script {

    init {
        itemOption("Empty") { (item, slot) ->
            val replacement: String = item.def.getOrNull("empty") ?: return@itemOption
            inventory.replace(slot, item.id, replacement)
            message("You empty the ${item.def.name.substringBefore(" (").lowercase()}.", ChatType.Filter)
        }

        itemOption("Empty Dish") { (item, slot) ->
            inventory.replace(slot, item.id, "pie_dish")
            message("You remove the burnt pie from the pie dish.", ChatType.Filter)
        }
    }
}
