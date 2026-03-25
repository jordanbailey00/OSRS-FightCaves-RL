package content.skill.mining

import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.chat.plural
import sim.engine.entity.character.player.chat.inventoryFull
import sim.engine.inv.add
import sim.engine.inv.addToLimit
import sim.engine.inv.inventory
import sim.engine.inv.removeToLimit

class CoalBag : Script {

    val bagCapacity = 81

    init {
        itemOption("Inspect", "coal_bag") {
            val coal = get("coal_bag_coal", 0)
            if (coal == 0) {
                message("Your coal bag is empty.")
            } else {
                message("Your coal bag has $coal ${"piece".plural(coal)} of coal in it.")
            }
        }

        itemOption("Withdraw-one", "coal_bag") {
            val coal = get("coal_bag_coal", 0)
            if (coal == 0) {
                message("There is no coal in your bag to withdraw.")
                return@itemOption
            }
            if (inventory.add("coal")) {
                set("coal_bag_coal", (coal - 1).coerceAtLeast(0))
            } else {
                inventoryFull()
            }
        }

        itemOption("Withdraw-many", "coal_bag") {
            val count = get("coal_bag_coal", 0)
            if (count == 0) {
                message("There is no coal in your bag to withdraw.")
                return@itemOption
            }
            val added = inventory.addToLimit("coal", count)
            if (added == 0) {
                return@itemOption
            }
            set("coal_bag_coal", (count - added).coerceAtLeast(0))
            message("You withdraw some coal.")
        }

        itemOnItem("coal", "coal_bag") { _, _ ->
            val coal = get("coal_bag_coal", 0)
            if (coal == bagCapacity) {
                message("The coal bag is already full.")
                return@itemOnItem
            }
            val limit = bagCapacity - coal
            val removed = inventory.removeToLimit("coal", limit)
            if (removed == 0) {
                return@itemOnItem
            }
            set("coal_bag_coal", (coal + removed).coerceAtMost(bagCapacity))
            message("You add the coal to your bag.")
        }

        destructible("coal_bag") {
            val coal = get("coal_bag_coal", 0)
            if (coal > 0) {
                message("You can't destroy this item with coal in it.")
                false
            } else {
                true
            }
        }
    }
}
