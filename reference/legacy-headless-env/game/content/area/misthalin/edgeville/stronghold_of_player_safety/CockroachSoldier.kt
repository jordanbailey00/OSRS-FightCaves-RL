package content.area.misthalin.edgeville.stronghold_of_player_safety

import sim.engine.Script
import sim.engine.entity.character.player.Player

class CockroachSoldier : Script {

    init {
        npcCondition("ranged_only") { target ->
            target is Player && tile.distanceTo(target.tile) > 2
        }
    }
}
