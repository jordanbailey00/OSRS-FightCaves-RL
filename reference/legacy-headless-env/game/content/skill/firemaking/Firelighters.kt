package content.skill.firemaking

import sim.engine.Script
import sim.engine.inv.inventory
import sim.engine.inv.transact.operation.RemoveItem.remove
import sim.engine.inv.transact.operation.ReplaceItem.replace

class Firelighters : Script {

    init {
        itemOnItem("logs", "*firelighter") { fromItem, toItem ->
            inventory.transaction {
                remove(fromItem.id)
                val colour = fromItem.id.removeSuffix("_firelighter")
                replace(toItem.id, "${colour}_logs")
            }
        }
    }
}
