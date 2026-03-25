package sim.engine.entity.character.mode.interact

import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player

class NPCOnPlayerInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("option"),
    )
    
    override val failedChecks = listOf(
        listOf("*"),
    )

    override val operate: Script.(List<String>, Caller) -> Unit = { args, caller ->
        npcOperatePlayer(args[0]) {
            caller.call()
        }
    }

    override val approach: Script.(List<String>, Caller) -> Unit = { args, caller ->
        npcApproachPlayer(args[0]) {
            caller.call()
        }
    }

    override fun interact() = NPCOnPlayerInteract(Player(), "option", NPC("npc_2"))

}