package content.area.fremennik_province.waterbirth_island_dungeon

import content.entity.effect.transform
import sim.engine.Script
import sim.engine.client.instruction.handle.interactPlayer
import sim.engine.entity.character.npc.NPCs
import sim.engine.queue.softQueue
import sim.engine.timer.toTicks
import java.util.concurrent.TimeUnit

class DagannothEggHatch : Script {

    init {
        /**
         * When a player enters hunt radius, egg transforms to spawn and attacks.
         */
        huntPlayer("dagannoth_egg", "aggressive") { target ->
            // Ignore if already transformed
            if (transform == "dagannoth_spawn") {
                return@huntPlayer
            }
            transform("dagannoth_egg_open")

            // Small stand-up delay before attacking
            softQueue("dagannoth_hatch_attack", 1) {
                transform("dagannoth_egg_opened")
                NPCs.add("dagannoth_spawn", tile)
                interactPlayer(target, "Attack")
            }
            softQueue("dagannoth_respawn", TimeUnit.MINUTES.toTicks(5)) {
                transform("dagannoth_egg")
            }
        }
    }
}
