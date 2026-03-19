package content.skill.summoning

import sim.engine.Script
import sim.engine.client.message

class Familiars : Script {
    init {
        npcOperate("Store", "*_familiar") {
            message("<dark_green>Not currently implemented.")
        }
    }
}
