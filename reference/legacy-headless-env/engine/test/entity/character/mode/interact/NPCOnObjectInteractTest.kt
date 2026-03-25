package sim.engine.entity.character.mode.interact

import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.obj.GameObject

class NPCOnObjectInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("option", "obj"),
        listOf("option", "*"),
    )
    
    override val failedChecks = listOf(
        listOf("*", "obj"),
    )

    override val operate: Script.(List<String>, Caller) -> Unit = { args, caller ->
        npcOperateObject(args[0]) {
            caller.call()
        }
    }

    override val approach: Script.(List<String>, Caller) -> Unit = { args, caller ->
        npcApproachObject(args[0]) {
            caller.call()
        }
    }

    override fun interact() = NPCOnObjectInteract(GameObject(0), "option", NPC("npc"))

}