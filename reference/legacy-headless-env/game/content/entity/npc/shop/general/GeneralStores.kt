package content.entity.npc.shop.general

import sim.engine.data.definition.InventoryDefinitions
import sim.engine.data.definition.ItemDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item
import sim.engine.get
import sim.engine.inv.Inventory
import sim.engine.inv.remove.ItemIndexAmountBounds
import sim.engine.inv.sendInventory
import sim.engine.inv.stack.AlwaysStack

object GeneralStores {

    val stores: MutableMap<String, Inventory> = mutableMapOf()

    fun get(key: String) = stores.getOrPut(key) {
        val definition = get<InventoryDefinitions>().get(key)
        val minimumQuantities = IntArray(definition.length) {
            val id = definition.ids?.getOrNull(it)
            if (id != -1 && id != null) -1 else 0
        }
        val checker = ItemIndexAmountBounds(minimumQuantities, 0)
        Inventory(
            data = Array(definition.length) {
                val id = definition.ids?.getOrNull(it)
                val amount = definition.amounts?.getOrNull(it)
                if (id == null) {
                    Item.EMPTY
                } else {
                    Item(ItemDefinitions.get(id).stringId, amount ?: 0)
                }
            },
            id = key,
            itemRule = GeneralStoreRestrictions,
            stackRule = AlwaysStack,
            amountBounds = checker,
        )
    }

    fun bind(player: Player, key: String): Inventory = get(key).apply {
        this.transaction.changes.bind(player)
        player.sendInventory(this, false)
    }

    fun unbind(player: Player, key: String): Inventory = get(key).apply {
        this.transaction.changes.unbind(player)
    }
}
