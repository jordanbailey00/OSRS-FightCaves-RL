package sim.engine.inv.transact

import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item
import sim.engine.inv.charges
import sim.engine.inv.transact.operation.AddChargeLimit.chargeToLimit
import sim.engine.inv.transact.operation.AddItem.add
import sim.engine.inv.transact.operation.ClearCharge.discharge
import sim.engine.inv.transact.operation.RemoveCharge.degrade
import sim.engine.inv.transact.operation.RemoveCharge.discharge
import sim.engine.inv.transact.operation.RemoveItem.remove

fun Transaction.remove(items: List<Item>) {
    for (item in items) {
        remove(item.id, item.amount)
    }
}

fun Transaction.add(items: List<Item>) {
    for (item in items) {
        add(item.id, item.amount)
    }
}

fun Transaction.charge(player: Player, index: Int, amount: Int) {
    if (failed) {
        return
    }
    val item = inventory.getOrNull(index)
    if (item == null || item.isEmpty()) {
        error = TransactionError.Invalid
        return
    }
    val variable: String? = item.def.getOrNull("charge")
    if (variable != null) {
        val start = item.def["charges", 0]
        val maximum = item.def["charges_max", start]
        val current = player[variable, start]
        player[variable] = (current + amount).coerceAtMost(maximum)
        state.onRevert {
            player[variable] = current
        }
        return
    }
    chargeToLimit(index, amount)
}

fun Transaction.discharge(player: Player, index: Int, amount: Int) {
    if (failed) {
        return
    }
    val item = inventory.getOrNull(index)
    if (item == null || item.isEmpty()) {
        error = TransactionError.Invalid
        return
    }
    val variable: String? = item.def.getOrNull("charge")
    if (variable != null) {
        val current = player[variable, 0]
        if (current < amount) {
            error = TransactionError.Deficient(current - amount)
            return
        }
        val reduced = (current - amount).coerceAtLeast(0)
        player[variable] = reduced
        state.onRevert {
            player[variable] = current
        }
        if (reduced <= 0) {
            degrade(item, index)
            player[variable] = item.def.getOrNull<Int>("charges") ?: 0
        }
        return
    }
    discharge(index, amount)
}

fun Transaction.clearCharges(player: Player, index: Int) {
    val item = inventory.getOrNull(index)
    if (failed || item == null || item.isEmpty()) {
        error = TransactionError.Invalid
        return
    }
    val variable: String? = item.def.getOrNull("charge")
    if (variable != null) {
        val current = player.clear(variable)
        if (current != null) {
            state.onRevert {
                player[variable] = current
            }
        }
        return
    }
    discharge(index)
}

fun Transaction.charges(player: Player, index: Int): Int = inventory.charges(player, index)
