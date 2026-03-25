package content.skill.woodcutting

import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.ItemDefinitions
import sim.engine.entity.item.drop.DropTables
import sim.engine.entity.item.drop.ItemDrop
import sim.engine.inv.add
import sim.engine.inv.inventory
import sim.engine.inv.replace

class BirdsNest(val drops: DropTables) : Script {

    init {
        itemOption("Search", "birds_nest_*") { (item, slot) ->
            val tableId = when (item.id) {
                "birds_nest_ring" -> "birds_nest_ring_table"
                "birds_nest_seeds_1" -> "birds_nest_seed_table"
                "birds_nest_seeds_2" -> "birds_nest_cheap_seed_table"
                "birds_nest_red_egg" -> "birds_nest_egg_red_table"
                "birds_nest_green_egg" -> "birds_nest_egg_green_table"
                "birds_nest_blue_egg" -> "birds_nest_egg_blue_table"
                "birds_nest_raven_egg" -> "birds_nest_egg_raven_table"
                else -> return@itemOption
            }

            val table = drops.get(tableId) ?: return@itemOption
            val items = mutableListOf<ItemDrop>()
            table.roll(list = items)

            val drop = items.firstOrNull() ?: return@itemOption
            val itemId = drop.id
            val itemAmount = drop.amount.first

            if (inventory.isFull()) {
                message("Your inventory is too full to take anything out of the bird's nest.")
                return@itemOption
            }

            val itemName = ItemDefinitions.get(drop.id).name.lowercase()
            if (inventory.replace(slot, item.id, "birds_nest_empty")) {
                inventory.add(itemId, itemAmount)
            }

            if (itemId.contains("acorn") || itemId.contains("orange") || itemId.contains("apple_tree") || itemId.contains("emerald")) {
                if (itemAmount > 1) {
                    message("You take $itemAmount ${itemName}s out of the bird's nest.")
                } else {
                    message("You take an $itemName out of the bird's nest.")
                }
            } else {
                if (itemAmount > 1) {
                    message("You take $itemAmount ${itemName}s out of the bird's nest.")
                } else {
                    message("You take a $itemName out of the bird's nest.")
                }
            }
        }
    }
}
