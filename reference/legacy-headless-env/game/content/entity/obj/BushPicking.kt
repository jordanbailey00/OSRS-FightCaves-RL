package content.entity.obj

import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.Settings
import sim.engine.entity.character.sound
import sim.engine.entity.obj.replace
import sim.engine.inv.add
import sim.engine.inv.inventory

class BushPicking : Script {

    init {
        objectOperate("Pick-from", "cadava_bush_full,cadava_bush_half") { (target) ->
            if (!inventory.add("cadava_berries")) {
                message("Your inventory is too full to pick the berries from the bush.")
                return@objectOperate
            }
            sound("pick")
            anim("picking_low")
            target.replace(if (target.id == "cadava_bush_full") "cadava_bush_half" else "cadava_bush_empty", ticks = Settings["world.objs.cadava.regrowTicks", 200])
        }

        objectOperate("Pick-from", "redberry_bush_full,redberry_bush_half") { (target) ->
            if (!inventory.add("redberries")) {
                message("Your inventory is too full to pick the berries from the bush.")
                return@objectOperate
            }
            sound("pick")
            anim("picking_low")
            target.replace(if (target.id == "redberry_bush_full") "redberry_bush_half" else "redberry_bush_empty", ticks = Settings["world.objs.redberry.regrowTicks", 200])
        }

        objectOperate("Pick-from", "cadava_bush_empty,redberry_bush_empty") {
            message("There are no berries on this bush at the moment.")
        }
    }
}
