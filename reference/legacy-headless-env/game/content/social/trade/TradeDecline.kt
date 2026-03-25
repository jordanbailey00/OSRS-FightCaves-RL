package content.social.trade

import content.social.trade.Trade.getPartner
import content.social.trade.Trade.isTradeInterface
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.closeMenu
import sim.engine.client.ui.menu
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.ChatType

class TradeDecline : Script {

    init {
        interfaceOption("Decline", "trade_main:decline") {
            decline()
        }

        interfaceOption("Decline", "trade_confirm:decline") {
            decline()
        }

        interfaceOption("Close", "trade_main:close") {
            decline()
        }

        interfaceOption("Close", "trade_confirm:close") {
            decline()
        }

        playerDespawn {
            if (isTradeInterface(menu)) {
                val other = getPartner(this)
                closeMenu()
                other?.message("Other player declined trade.", ChatType.Trade)
                other?.closeMenu()
            }
        }
    }

    /**
     * Declining or closing cancels the trade
     */

    fun Player.decline() {
        val other = getPartner(this)
        message("Declined trade.", ChatType.Trade)
        other?.message("Other player declined trade.", ChatType.Trade)
        closeMenu()
        other?.closeMenu()
    }
}
