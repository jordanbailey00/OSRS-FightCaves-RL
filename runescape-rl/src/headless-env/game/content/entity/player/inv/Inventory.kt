package content.entity.player.inv

import com.github.michaelbull.logging.InlineLogger
import content.entity.combat.attacking
import sim.engine.Script
import sim.engine.client.ui.InterfaceApi
import sim.engine.client.ui.ItemOption
import sim.engine.client.ui.closeInterfaces
import sim.engine.entity.character.mode.EmptyMode
import sim.engine.inv.inventory
import sim.engine.inv.sendInventory
import sim.engine.inv.swap

class Inventory : Script {

    val logger = InlineLogger()

    init {
        interfaceRefresh("inventory") { id ->
            interfaceOptions.unlockAll(id, "inventory", 0 until 28)
            interfaceOptions.unlock(id, "inventory", 28 until 56, "Drag")
            sendInventory(id)
        }

        interfaceSwap { _, _, _, _ ->
            queue.clearWeak()
        }

        interfaceSwap("inventory:*") { _, _, fromSlot, toSlot ->
            closeInterfaces()
            if (attacking) {
                mode = EmptyMode
            }
            if (!inventory.swap(fromSlot, toSlot)) {
                logger.info { "Failed switching interface items $this" }
            }
        }

        interfaceOption(id = "inventory:inventory") { (item, itemSlot, _, optionIndex, id) ->
            val itemDef = item.def
            val equipOption = when (optionIndex) {
                6 -> itemDef.options.getOrNull(3)
                7 -> itemDef.options.getOrNull(4)
                9 -> "Examine"
                else -> itemDef.options.getOrNull(optionIndex)
            }
            if (equipOption == null) {
                logger.info { "Unknown item option $item $optionIndex" }
                return@interfaceOption
            }
            closeInterfaces()
            if (attacking) {
                mode = EmptyMode
            }
            InterfaceApi.itemOption(this, ItemOption(item, itemSlot, id.substringBefore(":"), equipOption))
        }
    }
}
