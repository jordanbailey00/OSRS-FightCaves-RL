package content.area.asgarnia.taverley

import content.entity.obj.door.enterDoor
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.sound
import sim.engine.inv.inventory

class DustyKey : Script {

    init {
        objectOperate("Open", "gate_63_closed") { (target) ->
            if (inventory.contains("dusty_key")) {
                sound("unlock")
                enterDoor(target)
            } else {
                sound("locked")
                message("The gate is locked.")
            }
        }
    }
}
