package content.entity.npc.combat

import sim.engine.Script
import sim.engine.client.instruction.handle.interactNpc
import sim.engine.client.instruction.handle.interactPlayer
import sim.engine.data.Settings
import sim.engine.data.definition.Areas
import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.combat.CombatMovement
import sim.engine.entity.character.mode.interact.Interact
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player

class Aggression : Script {

    init {
        huntPlayer(mode = "aggressive", handler = ::playerHandler)

        huntPlayer(mode = "aggressive_intolerant", handler = ::playerHandler)

        huntPlayer(mode = "cowardly") { target ->
            if (!Settings["world.npcs.aggression", true] || attacking(this, target)) {
                return@huntPlayer
            }
            if (Settings["world.npcs.safeZone", false] && tile in Areas["lumbridge"]) {
                return@huntPlayer
            }
            interactPlayer(target, "Attack")
        }

        huntNPC(mode = "aggressive", handler = ::npcHandler)
        huntNPC(mode = "aggressive_intolerant", handler = ::npcHandler)

        huntNPC(mode = "cowardly") { target ->
            if (attacking(this, target)) {
                return@huntNPC
            }
            interactNpc(target, "Attack")
        }
    }

    fun playerHandler(npc: NPC, target: Player) {
        if (!Settings["world.npcs.aggression", true] || attacking(npc, target)) {
            return
        }
        if (Settings["world.npcs.safeZone", false] && npc.tile in Areas["lumbridge"]) {
            return
        }
        npc.interactPlayer(target, "Attack")
    }

    fun npcHandler(npc: NPC, target: NPC) {
        if (!attacking(npc, target)) {
            npc.interactNpc(target, "Attack")
        }
    }

    fun attacking(npc: NPC, target: Character): Boolean {
        val current = npc.mode
        if (current is Interact && current.target == target) {
            return true
        } else if (current is CombatMovement && current.target == target) {
            return true
        }
        return false
    }
}
