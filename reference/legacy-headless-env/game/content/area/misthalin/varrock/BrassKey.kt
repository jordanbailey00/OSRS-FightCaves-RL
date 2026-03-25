package content.area.misthalin.varrock

import content.entity.obj.door.enterDoor
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.sound
import sim.engine.inv.inventory

class BrassKey : Script {

    init {
        objectOperate("Open", "edgeville_dungeon_door_closed") { (target) ->
            if (inventory.contains("brass_key")) {
                sound("unlock")
                enterDoor(target)
            } else {
                sound("locked")
                message("The door is locked. You need a brass key to open it.")
            }
        }
    }
}
