package content.area.fremennik_province.rellekka

import sim.engine.Script
import sim.engine.client.instruction.handle.interactPlayer
import sim.engine.client.variable.hasClock

class RellekkaGuards : Script {

    init {
        huntPlayer("market_guard_rellekka", "guarding") { target ->
            if (target.hasClock("thieving")) {
                say("Hey, what do you think you are doing!")
                interactPlayer(target, "Attack")
            }
        }
    }
}
