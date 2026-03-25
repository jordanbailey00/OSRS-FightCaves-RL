package sim.engine.entity.character.mode.interact

import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.item.floor.FloorItem
import sim.types.Tile

class NPCOnFloorItemInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("option"),
    )

    override val failedChecks = listOf(
        listOf("*"),
    )

    override val operate: Script.(List<String>, Caller) -> Unit = { args, caller ->
        npcOperateFloorItem(args[0]) {
            caller.call()
        }
    }

    override val approach: Script.(List<String>, Caller) -> Unit = { args, caller ->
        npcApproachFloorItem(args[0]) {
            caller.call()
        }
    }

    override fun interact() = NPCOnFloorItemInteract(FloorItem(Tile.EMPTY, "floor_item"), "option", NPC("npc"), null)

}