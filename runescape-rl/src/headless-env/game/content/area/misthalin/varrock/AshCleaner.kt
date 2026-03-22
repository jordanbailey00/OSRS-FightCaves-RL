package content.area.misthalin.varrock

import sim.engine.Script
import sim.engine.client.instruction.handle.interactFloorItem

class AshCleaner : Script {

    init {
        huntFloorItem("ash_finder") { target ->
            interactFloorItem(target, "Take")
        }
    }
}
