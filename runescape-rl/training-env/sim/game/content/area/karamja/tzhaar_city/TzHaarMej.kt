package content.area.karamja.tzhaar_city

import content.entity.combat.inCombat
import content.entity.combat.target
import sim.engine.Script
import sim.engine.client.instruction.handle.interactPlayer
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.map.Spiral
import sim.types.Tile

class TzHaarMej : Script {

    init {
        huntPlayer("tzhaar_mej", "tzhaar_hunter") { target ->
            val victim = target.target
            if (victim !is NPC || !victim.id.startsWith("tzhaar_hur")) {
                return@huntPlayer
            }
            var helpers = findHelpers(tile)
            say("Kot e TzHaar-Hur!")
            interactPlayer(target, "Attack")
            for (helper in helpers) {
                helper.interactPlayer(target, "Attack")
            }
        }

        npcCombatStart { target ->
            if (target !is Player || !id.startsWith("tzhaar_mej")) {
                return@npcCombatStart
            }
            val helpers = findHelpers(tile)
            if (helpers.isEmpty()) {
                return@npcCombatStart
            }
            say("Kot kl, zek e JalYt!")
            for (helper in helpers) {
                helper.interactPlayer(target, "Attack")
            }
        }
    }

    private fun findHelpers(tile: Tile): MutableList<NPC> {
        val helpers = mutableListOf<NPC>()
        for (tile in Spiral.spiral(tile, 6)) {
            for (npc in NPCs.at(tile)) {
                if (npc.inCombat) {
                    continue
                }
                if (npc.id.startsWith("tzhaar_xil")) {
                    helpers.add(npc)
                } else if (npc.id.startsWith("tzhaar_ket")) {
                    helpers.add(npc)
                }
            }
        }
        return helpers
    }
}
