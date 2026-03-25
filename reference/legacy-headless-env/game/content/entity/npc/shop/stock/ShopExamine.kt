package content.entity.npc.shop.stock

import content.entity.npc.shop.shopInventory
import sim.engine.Script
import sim.engine.client.message

class ShopExamine : Script {

    init {
        interfaceOption("Examine", "shop_side:inventory") { (item) ->
            val examine: String = item.def.getOrNull("examine") ?: return@interfaceOption
            message(examine)
        }

        interfaceOption("Examine", "shop:sample") { (_, itemSlot) ->
            val item = shopInventory(true)[itemSlot / 4]
            val examine: String = item.def.getOrNull("examine") ?: return@interfaceOption
            message(examine)
        }

        interfaceOption("Examine", "shop:stock") { (_, itemSlot) ->
            val item = shopInventory(false)[itemSlot / 6]
            val examine: String = item.def.getOrNull("examine") ?: return@interfaceOption
            message(examine)
        }
    }
}
