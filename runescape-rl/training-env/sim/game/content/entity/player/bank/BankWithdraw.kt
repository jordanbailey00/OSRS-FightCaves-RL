package content.entity.player.bank

import com.github.michaelbull.logging.InlineLogger
import content.entity.player.dialogue.type.intEntry
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.menu
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.inventoryFull
import sim.engine.entity.item.Item
import sim.engine.inv.inventory
import sim.engine.inv.transact.TransactionError
import sim.engine.inv.transact.operation.MoveItemLimit.moveToLimit
import sim.engine.inv.transact.operation.ShiftItem.shiftToFreeIndex

class BankWithdraw : Script {

    val logger = InlineLogger()

    init {
        interfaceOption(id = "bank:inventory") { (item, itemSlot, option) ->
            val amount = when (option) {
                "Withdraw-1" -> 1
                "Withdraw-5" -> 5
                "Withdraw-10" -> 10
                "Withdraw-*" -> get("last_bank_amount", 0)
                "Withdraw-All" -> bank.count(item.id)
                "Withdraw-All but one" -> item.amount - 1
                "Withdraw-X" -> intEntry("Enter amount:").also {
                    set("last_bank_amount", it)
                }
                else -> return@interfaceOption
            }
            withdraw(this, item, itemSlot, amount)
        }

        interfaceOption("Toggle item/note withdrawl", "bank:note_mode") {
            toggle("bank_notes")
        }
    }

    fun withdraw(player: Player, item: Item, index: Int, amount: Int) {
        if (player.menu != "bank" || amount < 1) {
            return
        }

        val note = player["bank_notes", false]
        val noted = if (note) item.noted ?: item else item
        if (note && noted.id == item.id) {
            player.message("This item cannot be withdrawn as a note.")
        }
        var removed = false
        var moved = 0
        player.bank.transaction {
            val inv = player.inventory
            moved = moveToLimit(item.id, amount, inv, noted.id)
            if (moved <= 0) {
                error = TransactionError.Full()
                return@transaction
            }
            if (inventory[index].isEmpty()) {
                shiftToFreeIndex(index)
                removed = true
            }
        }
        when (player.bank.transaction.error) {
            TransactionError.None -> {
                if (moved < amount) {
                    player.inventoryFull("to withdraw that many")
                }
                if (removed) {
                    Bank.decreaseTab(player, Bank.getTab(player, index))
                }
            }
            is TransactionError.Full -> player.inventoryFull()
            TransactionError.Invalid -> logger.info { "Bank withdraw issue: $player $item $amount" }
            else -> {}
        }
    }
}
