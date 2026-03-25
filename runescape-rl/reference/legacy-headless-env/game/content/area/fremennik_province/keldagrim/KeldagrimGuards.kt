package content.area.fremennik_province.keldagrim

import sim.engine.Script
import sim.engine.client.instruction.handle.interactPlayer
import sim.engine.client.variable.hasClock

class KeldagrimGuards : Script {

    init {
        huntPlayer("black_guard_keldagrim_market*", "guarding") { target ->
            if (target.hasClock("thieving")) {
                say("Hey, what do you think you are doing!")
                interactPlayer(target, "Attack")
            }
        }
    }
}
