package content.entity.player.inv.item.take

import com.github.michaelbull.logging.InlineLogger
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.chat.inventoryFull
import sim.engine.entity.character.sound
import sim.engine.entity.item.floor.FloorItems
import sim.engine.event.AuditLog
import sim.engine.inv.Items
import sim.engine.inv.inventory
import sim.engine.inv.transact.TransactionError
import sim.engine.inv.transact.operation.AddItem.add
import sim.engine.inv.transact.operation.SetCharge.setCharge

class ItemTake : Script {

    val logger = InlineLogger()

    init {
        floorItemOperate("Take") { (target) ->
            arriveDelay()
            approachRange(-1)
            val item = Items.takeable(this, target.id) ?: return@floorItemOperate
            if (inventory.isFull() && (!inventory.stackable(item) || !inventory.contains(item))) {
                inventoryFull()
                return@floorItemOperate
            }
            if (!FloorItems.remove(target)) {
                message("Too late - it's gone!")
                return@floorItemOperate
            }

            inventory.transaction {
                val freeIndex = inventory.freeIndex()
                add(item, target.amount)
                if (target.charges > 0) {
                    setCharge(freeIndex, target.charges)
                }
            }
            when (inventory.transaction.error) {
                TransactionError.None -> {
                    AuditLog.event(this, "took", target, target.tile)
                    if (tile != target.tile) {
                        face(target.tile.delta(tile))
                        anim("take")
                    }
                    sound("take_item")
                    Items.take(this, target)
                }
                is TransactionError.Full -> inventoryFull()
                else -> logger.warn { "Error taking item $target ${inventory.transaction.error}" }
            }
        }

        npcOperateFloorItem("Take") { (target) ->
            arriveDelay()
            if (!FloorItems.remove(target)) {
                logger.warn { "$this unable to take $target." }
            }
            if (id == "ash_cleaner") {
                anim("cleaner_sweeping")
                delay(2)
                clearAnim()
            }
        }
    }
}
