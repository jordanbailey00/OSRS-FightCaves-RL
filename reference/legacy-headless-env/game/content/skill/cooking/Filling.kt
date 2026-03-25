package content.skill.cooking

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.inv.inventory
import sim.engine.inv.replace

class Filling : Script {

    init {
        itemOnObjectOperate(obj = "sink*,fountain*,well*,water_trough*,pump_and_drain*") { (target, item) ->
            if (!item.def.contains("full")) {
                return@itemOnObjectOperate
            }
            while (inventory.contains(item.id)) {
                anim("take")
                inventory.replace(item.id, item.def["full"])
                delay(if (item.id == "vase") 3 else 1)
                message("You fill the ${item.def.name.substringBefore(" (").lowercase()} from the ${target.def.name.lowercase()}", ChatType.Filter)
            }
        }
    }
}
