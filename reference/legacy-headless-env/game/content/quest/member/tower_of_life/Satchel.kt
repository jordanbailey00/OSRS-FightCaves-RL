package content.quest.member.tower_of_life

import content.entity.player.dialogue.type.item
import net.pearx.kasechange.toLowerSpaceCase
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.Player
import sim.engine.inv.charges
import sim.engine.inv.inventory
import sim.engine.inv.transact.operation.AddItem.add
import sim.engine.inv.transact.operation.RemoveItem.remove
import sim.engine.inv.transact.operation.SetCharge.setCharge

class Satchel : Script {

    val cake = 0x1
    val banana = 0x2
    val sandwich = 0x4

    init {
        itemOption("Inspect", "*_satchel") {
            val inventory = inventories.inventory(it.inventory)
            val charges = inventory.charges(this, it.slot)
            val cake = if (charges and cake != 0) "one" else "no"
            val banana = if (charges and banana != 0) "one" else "no"
            val sandwich = if (charges and sandwich != 0) "one" else "no"
            item(it.item.id, 400, "The ${it.item.id.toLowerSpaceCase()}!<br>(Containing: $sandwich sandwich, $cake cake, and $banana banana)")
        }

        itemOption("Empty", "*_satchel") {
            val inventory = inventories.inventory(it.inventory)
            var charges = inventory.charges(this, it.slot)
            charges = withdraw(this, it.slot, charges, "banana", banana)
            charges = withdraw(this, it.slot, charges, "cake", cake)
            withdraw(this, it.slot, charges, "triangle_sandwich", sandwich)
        }

        itemOnItem("cake", "*_satchel") { _, _, fromSlot, toSlot ->
            val charges = inventory.charges(this, toSlot)
            if (charges and cake != 0) {
                message("You already have a cake in there.")
                return@itemOnItem
            }
            inventory.transaction {
                remove(fromSlot, "cake")
                setCharge(toSlot, charges + cake)
            }
        }

        itemOnItem("banana", "*_satchel") { _, _, fromSlot, toSlot ->
            val charges = inventory.charges(this, toSlot)
            if (charges and banana != 0) {
                message("You already have a banana in there.")
                return@itemOnItem
            }
            inventory.transaction {
                remove(fromSlot, "banana")
                setCharge(toSlot, charges + banana)
            }
        }

        itemOnItem("triangle_sandwich", "*_satchel") { _, _, fromSlot, toSlot ->
            val charges = inventory.charges(this, toSlot)
            if (charges and sandwich != 0) {
                message("You already have a sandwich in there.")
                return@itemOnItem
            }
            inventory.transaction {
                remove(fromSlot, "triangle_sandwich")
                setCharge(toSlot, charges + sandwich)
            }
        }
    }

    fun withdraw(player: Player, slot: Int, charges: Int, id: String, food: Int): Int {
        if (charges and food != 0) {
            val success = player.inventory.transaction {
                add(id)
                setCharge(slot, charges and food.inv())
            }
            if (success) {
                return charges and food.inv()
            } else {
                player.message("You don't have enough free space to empty your satchel.")
            }
        }
        return charges
    }
}
