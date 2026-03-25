package content.entity.player.bank

import content.entity.player.bank.Bank.tabIndex
import sim.engine.Script
import sim.engine.inv.transact.operation.ShiftItem.shiftToFreeIndex

class BankTabCollapse : Script {

    init {
        interfaceOption("Collapse", "bank:tab_#") {
            val tab = it.component.removePrefix("tab_").toInt() - 1
            val tabIndex = tabIndex(this, tab)
            val count: Int = get("bank_tab_$tab", 0)
            val collapsed = bank.transaction {
                repeat(count) {
                    shiftToFreeIndex(tabIndex)
                }
            }
            if (collapsed) {
                repeat(count) {
                    Bank.decreaseTab(this, tab)
                }
            }
        }
    }
}
