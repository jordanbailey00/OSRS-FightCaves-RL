package content.area.asgarnia.taverley

import sim.engine.Script
import sim.engine.entity.character.mode.Follow
import sim.engine.entity.character.npc.NPCs

class Bettamax : Script {

    init {
        npcSpawn("wilbur") {
            val bettamax = NPCs.find(tile.zone, "bettamax")
            mode = Follow(this, bettamax)
            watch(bettamax)
        }
    }
}
