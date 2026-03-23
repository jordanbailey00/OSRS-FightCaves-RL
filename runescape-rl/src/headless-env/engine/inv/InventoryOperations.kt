package sim.engine.inv

import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item
import sim.engine.inv.transact.charge
import sim.engine.inv.transact.clearCharges
import sim.engine.inv.transact.discharge
import sim.engine.inv.transact.operation.AddItem.add
import sim.engine.inv.transact.operation.AddItemLimit.addToLimit
import sim.engine.inv.transact.operation.ClearItem.clear
import sim.engine.inv.transact.operation.MoveItem.move
import sim.engine.inv.transact.operation.MoveItem.moveAll
import sim.engine.inv.transact.operation.MoveItemLimit.moveToLimit
import sim.engine.inv.transact.operation.RemoveItem.remove
import sim.engine.inv.transact.operation.RemoveItemLimit.removeToLimit
import sim.engine.inv.transact.operation.ReplaceItem.replace
import sim.engine.inv.transact.operation.ShiftItem.shift
import sim.engine.inv.transact.operation.SwapItem.swap

fun Inventory.replace(id: String, with: String) = transaction { replace(id, with) }

fun Inventory.replace(index: Int, id: String, with: String) = transaction { replace(index, id, with) }

fun Inventory.swap(fromIndex: Int, toIndex: Int) = transaction { swap(fromIndex, toIndex) }

fun Inventory.swap(fromIndex: Int, target: Inventory, toIndex: Int) = transaction { swap(fromIndex, target, toIndex) }

fun Inventory.moveAll(target: Inventory) = transaction { moveAll(target) }

fun Inventory.move(fromIndex: Int, toIndex: Int) = transaction { move(fromIndex, toIndex) }

fun Inventory.move(fromIndex: Int, target: Inventory) = transaction { move(fromIndex, target) }

fun Inventory.move(fromIndex: Int, target: Inventory, toIndex: Int) = transaction { move(fromIndex, target, toIndex) }

fun Inventory.moveToLimit(id: String, amount: Int, target: Inventory): Int {
    var moved = 0
    transaction {
        moved = this.moveToLimit(id, amount, target)
    }
    return moved
}

fun Inventory.shift(fromIndex: Int, toIndex: Int) = transaction { shift(fromIndex, toIndex) }

fun Inventory.add(id: String, amount: Int = 1) = transaction { add(id, amount) }

fun Inventory.add(vararg ids: String, amount: Int = 1) = transaction {
    for (id in ids) {
        add(id, amount)
    }
}

fun Inventory.add(items: List<Item>) = transaction {
    for (item in items) {
        add(item.id, item.amount)
    }
}

fun Inventory.addToLimit(id: String, amount: Int = 1): Int {
    var added = 0
    transaction {
        added = this.addToLimit(id, amount)
    }
    return added
}

fun Inventory.remove(id: String, amount: Int = 1) = transaction { remove(id, amount) }

fun Inventory.remove(index: Int, id: String, amount: Int = 1) = transaction { remove(index, id, amount) }

fun Inventory.remove(vararg ids: String, amount: Int = 1) = transaction {
    for (id in ids) {
        remove(id, amount)
    }
}

fun Inventory.remove(items: List<Item>) = transaction {
    for (item in items) {
        remove(item.id, item.amount)
    }
}

fun Inventory.removeToLimit(id: String, amount: Int = 1): Int {
    var removed = 0
    transaction {
        removed = this.removeToLimit(id, amount)
    }
    return removed
}

fun Inventory.clear(index: Int) = transaction { clear(index) }

fun Inventory.clear() = transaction { clear() }

fun Inventory.contains(items: List<Item>) = items.all { item -> contains(item.id, item.amount) }

fun Inventory.any(items: List<Item>) = items.any { item -> contains(item.id, item.amount) }

fun Inventory.charge(player: Player, index: Int, amount: Int = 1) = transaction { charge(player, index, amount) }

fun Inventory.discharge(player: Player, index: Int, amount: Int = 1) = transaction { discharge(player, index, amount) }

fun Inventory.clearCharges(player: Player, index: Int) = transaction { clearCharges(player, index) }

fun Inventory.charges(player: Player, index: Int): Int {
    val item = getOrNull(index) ?: return 0
    return item.charges(player)
}

fun Item.charges(player: Player): Int {
    if (isEmpty() || !def.contains("charges")) {
        return 0
    }
    val variable: String? = def.getOrNull("charge")
    if (variable != null) {
        return player[variable, 0]
    }
    return value
}

fun Item.charges(): Int {
    if (isEmpty() || !def.contains("charges")) {
        return 0
    }
    return value
}
