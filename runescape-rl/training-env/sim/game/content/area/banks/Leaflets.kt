package content.area.banks

import sim.engine.Script
import sim.engine.client.message
import sim.engine.inv.add
import sim.engine.inv.inventory

class Leaflets : Script {

    init {
        objectOperate("Take", "*_bank_leaflet") {
            if (inventory.contains("leaflet")) {
                message("You already have a copy of the leaflet.")
            } else {
                inventory.add("leaflet")
            }
        }
    }
}
