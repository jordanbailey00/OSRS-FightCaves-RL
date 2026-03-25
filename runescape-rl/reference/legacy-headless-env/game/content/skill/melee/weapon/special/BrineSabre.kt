package content.skill.melee.weapon.special

import sim.engine.Script
import sim.engine.client.message

class BrineSabre : Script {

    init {
        specialAttackPrepare("brine_sabre") {
            if (tile.region.id != 11924) {
                message("You can only use this special attack under water.")
                false
            } else {
                true
            }
        }
    }
}
