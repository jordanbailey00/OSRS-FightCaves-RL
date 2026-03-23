package content.area.misthalin.edgeville

import sim.engine.Script
import sim.engine.data.definition.Areas
import sim.types.Tile

class Wilderness : Script {

    val wilderness = Areas["wilderness"]
    val safeZones = Areas.tagged("safe_zone")

    init {
        playerSpawn {
            if (inWilderness(tile)) {
                set("in_wilderness", true)
            }
        }

        moved { from ->
            val was = inWilderness(from)
            val now = inWilderness(tile)
            if (!was && now) {
                set("in_wilderness", true)
            } else if (was && !now) {
                clear("in_wilderness")
            }
        }
    }

    fun inWilderness(tile: Tile) = tile in wilderness && safeZones.none { tile in it.area }
}
