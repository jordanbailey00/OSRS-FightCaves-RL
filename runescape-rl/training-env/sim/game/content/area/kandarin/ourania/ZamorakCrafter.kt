package content.area.kandarin.ourania

import sim.engine.Script
import sim.engine.data.definition.PatrolDefinitions
import sim.engine.entity.character.mode.Patrol
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.obj.GameObjects
import sim.engine.queue.strongQueue
import sim.types.Tile
import sim.types.equals

class ZamorakCrafter(val patrols: PatrolDefinitions) : Script {

    init {
        npcSpawn("zamorak_crafter*") {
            val patrol = patrols.get(if (id == "zamorak_crafter_start") "zamorak_crafter_to_altar" else "zamorak_crafter_to_bank")
            mode = Patrol(this, patrol.waypoints)
        }

        npcMoved("zamorak_crafter*", ::checkRoute)
    }

    fun checkRoute(npc: NPC, from: Tile) {
        if (npc.tile.equals(3314, 4811)) {
            npc.strongQueue("craft_runes") {
                val altar = GameObjects.findOrNull(Tile(3315, 4810), "ourania_altar")
                if (altar != null) {
                    npc.face(altar)
                }
                npc.delay(4)
                npc.anim("bind_runes")
                npc.gfx("bind_runes")
                npc.delay(4)
                val patrol = patrols.get("zamorak_crafter_to_bank")
                npc.mode = Patrol(npc, patrol.waypoints)
            }
        } else if (npc.tile.equals(3270, 4856)) {
            npc.strongQueue("return_home") {
                npc.delay(5)
                val patrol = patrols.get("zamorak_crafter_to_altar")
                npc.mode = Patrol(npc, patrol.waypoints)
            }
        }
    }
}
