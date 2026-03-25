package content.entity.player.inv.item.drop

import content.entity.player.inv.item.tradeable
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.World
import sim.engine.entity.character.sound
import sim.engine.entity.item.floor.FloorItems
import sim.engine.inv.inventory
import sim.engine.inv.remove

class ItemPlace : Script {

    init {
        itemOnObjectOperate(obj = "table*") { (target, item, slot) ->
            if (!World.members && item.def["members", false]) {
                message("To use this item please login to a members' server.")
                return@itemOnObjectOperate
            }
            if (!item.tradeable) {
                message("You cannot put that on a table.")
                return@itemOnObjectOperate
            }
            if (inventory.remove(slot, item.id, item.amount)) {
                anim("take")
                sound("drop_item")
                val tile = target.nearestTo(tile)
                FloorItems.add(tile, item.id, item.amount, revealTicks = 100, disappearTicks = 1000, owner = this)
            }
        }
    }
}
