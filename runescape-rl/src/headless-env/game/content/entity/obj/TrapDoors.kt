package content.entity.obj

import sim.engine.Script
import sim.engine.entity.obj.replace
import sim.engine.timer.toTicks
import java.util.concurrent.TimeUnit

class TrapDoors : Script {

    init {
        objectOperate("Open", "trapdoor_*_closed") { (target) ->
            anim("open_chest")
            target.replace(target.id.replace("_closed", "_opened"), ticks = TimeUnit.MINUTES.toTicks(3))
        }

        objectOperate("Close", "trapdoor_*_opened") { (target) ->
            anim("close_chest")
            target.replace(target.id.replace("_opened", "_closed"), ticks = TimeUnit.MINUTES.toTicks(3))
        }
    }
}
