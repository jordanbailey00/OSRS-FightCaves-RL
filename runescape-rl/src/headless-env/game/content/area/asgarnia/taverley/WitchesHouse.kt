package content.area.asgarnia.taverley

import sim.engine.Script
import sim.engine.data.definition.PatrolDefinitions
import sim.engine.entity.character.mode.Patrol

class WitchesHouse(val patrols: PatrolDefinitions) : Script {

    init {
        npcSpawn("nora_t_hagg") {
            val patrol = patrols.get("nora_t_hagg")
            mode = Patrol(this, patrol.waypoints)
        }
    }
}
