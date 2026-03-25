package sim.engine.entity.character.mode.interact

import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.npc.NPC

class NPCOnNPCInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("option"),
    )
    
    override val failedChecks = listOf(
        listOf("*"),
    )

    override val operate: Script.(List<String>, Caller) -> Unit = { args, caller ->
        npcOperateNPC(args[0]) {
            caller.call()
        }
    }

    override val approach: Script.(List<String>, Caller) -> Unit = { args, caller ->
        npcApproachNPC(args[0]) {
            caller.call()
        }
    }

    override fun interact() = NPCOnNPCInteract(NPC("npc_1"), "option", NPC("npc_2"))

}