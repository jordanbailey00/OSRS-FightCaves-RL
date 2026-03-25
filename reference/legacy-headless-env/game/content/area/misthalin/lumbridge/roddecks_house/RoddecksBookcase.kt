package content.area.misthalin.lumbridge.roddecks_house

import sim.engine.Script
import sim.engine.client.message
import sim.engine.inv.add
import sim.engine.inv.inventory

class RoddecksBookcase : Script {

    init {
        objectOperate("Search", "roddecks_bookcase") {
            if (inventory.contains("roddecks_diary") && inventory.contains("manual_unstable_foundations")) {
                message("There's nothing particularly interesting here.")
            } else {
                if (!inventory.contains("roddecks_diary")) {
                    inventory.add("roddecks_diary")
                }
                if (!inventory.contains("manual_unstable_foundations")) {
                    inventory.add("manual_unstable_foundations")
                }
            }
        }
    }
}
