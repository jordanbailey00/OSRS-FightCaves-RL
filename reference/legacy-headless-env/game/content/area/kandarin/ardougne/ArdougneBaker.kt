package content.area.kandarin.ardougne

import content.entity.npc.shop.openShop
import sim.engine.Script
import sim.types.equals

class ArdougneBaker : Script {

    init {
        npcOperate("Trade", "baker_ardougne") { (target) ->
            if (target.tile.equals(2669, 3310)) {
                openShop("ardougne_bakers_stall_east")
            } else {
                openShop("ardougne_bakers_stall_west")
            }
        }
    }
}
