package sim.engine.entity.character.mode.interact

import sim.engine.Script
import sim.engine.entity.Approachable
import sim.engine.entity.Operation
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.item.floor.FloorItem

data class NPCOnFloorItemInteract(
    override val target: FloorItem,
    override val option: String,
    val npc: NPC,
    val shape: Int?
) : InteractOption(npc, target, shape = shape) {
    override fun hasOperate() = Operation.npcFloorItem.containsKey(option)

    override fun hasApproach() = Approachable.npcFloorItem.containsKey(option)

    override fun operate() {
        invoke(Operation.npcFloorItem)
    }

    override fun approach() {
        invoke(Approachable.npcFloorItem)
    }

    private fun invoke(map: Map<String, List<suspend NPC.(NPCOnFloorItemInteract) -> Unit>>) {
        Script.launch {
            for (block in map[option] ?: return@launch) {
                block(npc, this@NPCOnFloorItemInteract)
            }
        }
    }
}