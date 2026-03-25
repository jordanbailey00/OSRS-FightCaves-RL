package content.area.misthalin.lumbridge.farm

import content.entity.player.bank.ownsItem
import content.quest.questCompleted
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.chat.inventoryFull
import sim.engine.entity.obj.replace
import sim.engine.inv.add
import sim.engine.inv.inventory
import sim.engine.timer.toTicks
import java.util.concurrent.TimeUnit

class SethGroatsFarm : Script {

    init {
        objectOperate("Take-hatchet", "hatchet_logs") { (target) ->
            if (inventory.add("bronze_hatchet")) {
                target.replace("logs", ticks = TimeUnit.MINUTES.toTicks(3))
            } else {
                inventoryFull()
            }
        }

        takeable("super_large_egg") { item ->
            if (questCompleted("cooks_assistant")) {
                message("You've no reason to pick that up; eggs of that size are only useful for royal cakes.")
                null
            } else if (ownsItem("super_large_egg")) {
                message("You've already got one of those eggs and one's enough.")
                null
            } else {
                item
            }
        }
    }
}
