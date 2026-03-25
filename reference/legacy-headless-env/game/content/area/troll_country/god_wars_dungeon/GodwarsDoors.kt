package content.area.troll_country.god_wars_dungeon

import content.entity.obj.door.enterDoor
import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.Areas

class GodwarsDoors : Script {

    init {
        objectOperate("Open", "big_door_saradomin_closed,big_door_bandos_closed,big_door_armadyl_closed,big_door_zamorak_closed") { (target) ->
            val god = target.id.removePrefix("big_door_").removeSuffix("_closed")
            if (tile in Areas["${god}_chamber"]) {
                enterDoor(target)
                return@objectOperate
            }

            if (get("${god}_killcount", 0) < 40) {
                message("You don't have enough kills to enter the lair of the gods.")
                return@objectOperate
            }
            dec("${god}_killcount", 40)
            enterDoor(target)
        }
    }
}
