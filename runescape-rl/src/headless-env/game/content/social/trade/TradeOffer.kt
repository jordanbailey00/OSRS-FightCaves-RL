package content.social.trade

import content.entity.player.dialogue.type.intEntry
import content.social.trade.Trade.isTrading
import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.ItemDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.inv.inventory
import sim.engine.inv.restrict.ItemRestrictionRule
import sim.engine.inv.transact.operation.AddItem.add
import sim.engine.inv.transact.operation.RemoveItemLimit.removeToLimit

class TradeOffer : Script {

    val tradeRestriction = object : ItemRestrictionRule {
        override fun restricted(id: String): Boolean {
            val def = ItemDefinitions.get(id)
            return def.lendTemplateId != -1 || def.dummyItem != 0 || !def["tradeable", true]
        }
    }

    init {
        playerSpawn {
            offer.itemRule = tradeRestriction
        }

        interfaceOption(id = "trade_side:offer") { (item, _, option) ->
            val amount = when (option) {
                "Offer" -> 1
                "Offer-5" -> 5
                "Offer-10" -> 10
                "Offer-All" -> Int.MAX_VALUE
                "Offer-X" -> intEntry("Enter amount:")
                else -> return@interfaceOption
            }
            offer(this, item.id, amount)
        }

        interfaceOption("Value", "trade_side:offer") { (item) ->
            message("${item.def.name} is priceless!", ChatType.Trade)
        }
    }

    /**
     * Offering an item to trade or loan
     */

    // Item must be tradeable and not lent or a dummy item

    fun offer(player: Player, id: String, amount: Int) {
        if (!isTrading(player, amount)) {
            return
        }
        val offered = player.inventory.transaction {
            val added = removeToLimit(id, amount)
            val transaction = link(player.offer)
            transaction.add(id, added)
        }
        if (!offered) {
            player.message("That item is not tradeable.")
        }
    }
}
