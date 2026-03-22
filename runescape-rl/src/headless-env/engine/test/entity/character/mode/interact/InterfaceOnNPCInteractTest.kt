package sim.engine.entity.character.mode.interact

import sim.cache.definition.data.NPCDefinition
import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player

class InterfaceOnNPCInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("id", "npc"),
        listOf("*", "npc"),
        listOf("id", "*"),
    )
    
    override val operate: Script.(args: List<String>, caller: Caller) -> Unit = { args, caller ->
        onNPCOperate(args[0], args[1]) {
            caller.call()
        }
    }

    override val approach: Script.(args: List<String>, caller: Caller) -> Unit = { args, caller ->
        onNPCApproach(args[0], args[1]) {
            caller.call()
        }
    }

    override fun interact() = InterfaceOnNPCInteract(NPC("npc", def = NPCDefinition(0, stringId = "npc")), "id", 0, Player())

}