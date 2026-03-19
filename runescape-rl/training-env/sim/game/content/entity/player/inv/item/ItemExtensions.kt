package content.entity.player.inv.item

import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item
import sim.engine.entity.item.floor.FloorItems
import sim.engine.inv.Inventory
import sim.engine.inv.add
import sim.engine.inv.inventory

val Item.tradeable: Boolean
    get() = def["tradeable", true]

fun Player.addOrDrop(id: String, amount: Int = 1, inventory: Inventory = this.inventory, revealTicks: Int = 100, disappearTicks: Int = 200): Boolean {
    if (!inventory.add(id, amount)) {
        FloorItems.add(tile, id, amount, revealTicks = revealTicks, disappearTicks = disappearTicks, owner = this)
        return false
    }
    return true
}
