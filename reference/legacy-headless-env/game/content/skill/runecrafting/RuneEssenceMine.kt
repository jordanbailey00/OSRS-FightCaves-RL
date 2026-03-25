package content.skill.runecrafting

import content.entity.proj.shoot
import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.Areas
import sim.engine.entity.character.move.tele
import sim.engine.queue.softQueue

class RuneEssenceMine : Script {

    init {
        objectOperate("Enter", "rune_essence_exit_portal") { (target) ->
            message("You step through the portal...")
            gfx("curse_impact", delay = 30)
            target.tile.shoot("curse", tile)

            softQueue("essence_mine_exit", 3) {
                val npc = get("last_npc_teleport_to_rune_essence_mine", "aubury")
                val tile = Areas["${npc}_return"].random()
                tele(tile)
            }
        }
    }
}
