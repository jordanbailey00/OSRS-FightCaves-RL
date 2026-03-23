package content.area.misthalin.edgeville.stronghold_of_player_safety

import sim.engine.Script
import sim.engine.client.message
import sim.types.random

class StrongholdOfPlayerSafetyShelf : Script {
    init {
        objectOperate("Search", "player_safety_shelf") {
            message("You search the books...")
            delay(2)
            when (random.nextInt(0, 3)) {
                0 -> message("None of them look very interesting.")
                1 -> message("You find nothing to interest you.")
                2 -> message("You don't find anything that you'd ever want to read.")
            }
        }
    }
}
