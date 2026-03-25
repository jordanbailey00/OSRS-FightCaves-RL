package content.skill.magic.weapon

import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.chat.plural
import sim.engine.entity.character.player.chat.inventoryFull
import sim.engine.inv.charges
import sim.engine.inv.inventory
import sim.engine.inv.transact.TransactionError
import sim.engine.inv.transact.charge
import sim.engine.inv.transact.discharge
import sim.engine.inv.transact.operation.AddItemLimit.addToLimit
import sim.engine.inv.transact.operation.RemoveItem.remove

class LawStaff : Script {

    init {
        playerSpawn {
            sendVariable("law_staff_charges")
        }

        itemOption("Inspect", "law_staff") {
            val charges = inventory.charges(this, it.slot)
            message("The staff has ${if (charges == 0) "no" else charges} ${"charge".plural(charges)}.")
        }

        itemOption("Empty", "law_staff") {
            val charges = inventory.charges(this, it.slot)
            if (charges == 0) {
                message("The staff has no charges for your to remove.")
                return@itemOption
            }
            val success = inventory.transaction {
                val added = addToLimit("law_rune", charges)
                if (added <= 0) {
                    error = TransactionError.Deficient(charges)
                } else {
                    discharge(this@itemOption, it.slot, added)
                }
            }
            println(inventory.charges(this, it.slot))
            if (success) {
                message("You remove charges from the staff and retrieve some law runes.")
            } else {
                inventoryFull()
            }
        }

        itemOnItem("law_rune", "law_staff") { fromItem, toItem, _, toSlot ->
            val maximum = toItem.def.getOrNull<Int>("charges_max") ?: return@itemOnItem
            val spaces = maximum - inventory.charges(this, toSlot)
            val count = inventory.count(fromItem.id).coerceAtMost(spaces)
            if (count <= 0) {
                message("The staff already has the maximum amount of charges.")
                return@itemOnItem
            }
            val success = inventory.transaction {
                remove(fromItem.id, count)
                charge(this@itemOnItem, toSlot, count)
            }
            if (success) {
                message("You charge the staff with law runes.")
            }
        }
    }
}
