package content.area.misthalin.lumbridge.castle

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.areaSound

class LumbridgeWinch : Script {

    init {
        objectOperate("Operate", "lumbridge_winch") { (target) ->
            message("It seems the winch is jammed. You can't move it.")
            areaSound("lever", target.tile)
        }
    }
}
