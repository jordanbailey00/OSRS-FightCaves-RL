package content.entity.npc.shop

import content.entity.npc.shop.general.GeneralStores
import content.entity.player.dialogue.Disheartened
import content.entity.player.dialogue.type.player
import sim.engine.client.ui.InterfaceApi
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.inventoryFull
import sim.engine.inv.Inventory
import sim.engine.inv.inventory
import sim.engine.inv.transact.TransactionError
import sim.engine.inv.transact.operation.AddItem.add
import sim.engine.inv.transact.operation.RemoveItem.remove

fun Player.hasShopSample(): Boolean = this["info_sample", false]

fun Player.shop(): String = this["shop", ""]

fun Player.shopInventory(sample: Boolean = hasShopSample()): Inventory {
    val shop: String = this["shop", ""]
    val name = if (sample) "${shop}_sample" else shop
    return if (name.endsWith("general_store")) {
        GeneralStores.bind(this, name)
    } else {
        inventories.inventory(name)
    }
}

fun Player.openShop(id: String) {
    InterfaceApi.openShop(this, id)
}

suspend fun Player.buy(item: String, cost: Int, message: String = "Oh dear. I don't seem to have enough money."): Boolean {
    inventory.transaction {
        remove("coins", cost)
        add(item)
    }
    when (inventory.transaction.error) {
        is TransactionError.Full -> inventoryFull()
        TransactionError.None -> return true
        else -> player<Disheartened>(message)
    }
    return false
}
