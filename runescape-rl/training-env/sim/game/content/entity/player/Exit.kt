package content.entity.player

import content.entity.combat.underAttack
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.open
import sim.engine.data.AccountManager

class Exit(val accounts: AccountManager) : Script {

    init {
        interfaceOption("Exit", "toplevel*:logout") {
            open("logout")
        }

        interfaceOption(id = "logout:*") {
            if (underAttack) {
                message("You can't log out until 8 seconds after the end of combat.")
                return@interfaceOption
            }
            accounts.logout(this, true)
        }
    }
}
