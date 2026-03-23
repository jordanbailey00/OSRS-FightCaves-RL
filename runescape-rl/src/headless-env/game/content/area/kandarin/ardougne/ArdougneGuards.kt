package content.area.kandarin.ardougne

import sim.engine.Script
import sim.engine.client.instruction.handle.interactPlayer
import sim.engine.client.variable.hasClock

class ArdougneGuards : Script {

    init {
        huntPlayer("market_guard_draynor", "guarding") { target ->
            if (target.hasClock("thieving")) {
                say("Hey, what do you think you are doing!")
                interactPlayer(target, "Attack")
            }
        }
    }
}
