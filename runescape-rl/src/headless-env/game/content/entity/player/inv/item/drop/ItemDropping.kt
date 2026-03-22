package content.entity.player.inv.item.drop

import com.github.michaelbull.logging.InlineLogger
import content.entity.player.inv.item.tradeable
import sim.engine.Script
import sim.engine.entity.character.sound
import sim.engine.entity.item.floor.FloorItems
import sim.engine.event.AuditLog
import sim.engine.inv.Items
import sim.engine.inv.charges
import sim.engine.inv.inventory
import sim.engine.inv.remove

class ItemDropping : Script {

    val logger = InlineLogger()

    init {
        itemOption("Drop") { (item, slot) ->
            queue.clearWeak()
            if (!Items.droppable(this, item)) {
                return@itemOption
            }
            AuditLog.event(this, "dropped", item, tile)
            if (inventory.remove(slot, item.id, item.amount)) {
                if (item.tradeable) {
                    FloorItems.add(tile, item.id, item.amount, revealTicks = 100, disappearTicks = 200, owner = this)
                } else {
                    FloorItems.add(tile, item.id, item.amount, charges = item.charges(), revealTicks = FloorItems.NEVER, disappearTicks = 300, owner = this)
                }
                Items.drop(this, item)
                sound("drop_item")
            } else {
                logger.info { "Error dropping item $item for $this" }
            }
        }
    }
}
