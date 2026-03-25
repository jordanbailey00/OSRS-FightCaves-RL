package content.area.misthalin.draynor_village

import sim.engine.Script
import sim.engine.client.instruction.handle.interactPlayer
import sim.engine.client.variable.hasClock

class DraynorGuards : Script {

    init {
        huntPlayer("guard_ardougne", "guarding") { target ->
            if (target.hasClock("thieving")) {
                say("Hey, what do you think you are doing!")
                interactPlayer(target, "Attack")
            }
        }

        huntPlayer("knight_of_ardougne", "guarding") { target ->
            if (target.hasClock("thieving") && target["stall_level", 0] >= 20) {
                say("Hey, what do you think you are doing!")
                interactPlayer(target, "Attack")
            }
        }
    }
}
