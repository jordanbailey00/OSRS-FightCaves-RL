package content.area.troll_country.god_wars_dungeon.saradomin

import sim.engine.Script
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs
import sim.types.Tile

class CommanderZilyana : Script {

    var starlight: NPC? = null
    var bree: NPC? = null
    var growler: NPC? = null

    init {
        npcSpawn("commander_zilyana") {
            if (starlight == null) {
                starlight = NPCs.add("starlight", Tile(2903, 5260))
            }
            if (bree == null) {
                bree = NPCs.add("bree", Tile(2902, 5270))
            }
            if (growler == null) {
                growler = NPCs.add("growler", Tile(2898, 5262))
            }
        }

        npcDespawn("starlight") {
            starlight = null
        }

        npcDespawn("bree") {
            bree = null
        }

        npcDespawn("growler") {
            growler = null
        }
    }
}
