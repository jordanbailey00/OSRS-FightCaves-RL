package content.entity.player.modal

import sim.engine.Script
import sim.engine.client.sendInterfaceItemUpdate
import sim.engine.data.definition.InventoryDefinitions
import sim.engine.data.definition.ItemDefinitions

class Containers(val inventoryDefinitions: InventoryDefinitions) : Script {

    init {
        inventoryUpdated { inventory, updates ->
            val secondary = inventory.startsWith("_")
            val id = if (secondary) inventory.removePrefix("_") else inventory
            sendInterfaceItemUpdate(
                key = inventoryDefinitions.get(id).id,
                updates = updates.map { Triple(it.index, ItemDefinitions.getOrNull(it.item.id)?.id ?: -1, it.item.amount) },
                secondary = secondary,
            )
        }
    }
}
