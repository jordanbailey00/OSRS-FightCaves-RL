package sim.engine.entity.character.mode.interact

import sim.cache.definition.data.NPCDefinition
import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item

class ItemOnNPCInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("item", "npc"),
        listOf("item", "*"),
        listOf("*", "npc"),
    )

    override val operate: Script.(List<String>, Caller) -> Unit = { args, caller ->
        itemOnNPCOperate(args[0], args[1]) {
            caller.call()
        }
    }

    override val approach: Script.(List<String>, Caller) -> Unit = { args, caller ->
        itemOnNPCApproach(args[0], args[1]) {
            caller.call()
        }
    }

    override fun interact() = ItemOnNPCInteract(NPC("npc", def = NPCDefinition(0, stringId = "npc")), Item("item"), 0, "id", Player())

}