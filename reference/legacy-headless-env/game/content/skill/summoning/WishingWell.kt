package content.skill.summoning

import content.entity.npc.shop.openShop
import sim.engine.Script

class WishingWell : Script {

    init {
        objectOperate("Make-wish", "wishing_well") {
            openShop("summoning_supplies")
        }
    }
}
