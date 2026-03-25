package sim.engine.entity.character.mode.interact

import sim.cache.definition.data.NPCDefinition
import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player

class PlayerOnNPCInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("option", "npc"),
        listOf("option", "*"),
    )

    override val failedChecks = listOf(
        listOf("*", "npc"),
        listOf("*", "*"),
    )

    override val operate: Script.(List<String>, Caller) -> Unit = { args, caller ->
        npcOperate(args[0], args[1]) {
            caller.call()
        }
    }

    override val approach: Script.(List<String>, Caller) -> Unit = { args, caller ->
        npcApproach(args[0], args[1]) {
            caller.call()
        }
    }

    override fun interact() = PlayerOnNPCInteract(NPC("npc", def = NPCDefinition(0, stringId = "npc")),"option", Player())

}