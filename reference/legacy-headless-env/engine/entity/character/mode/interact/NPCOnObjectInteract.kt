package sim.engine.entity.character.mode.interact

import sim.engine.Script
import sim.engine.entity.Approachable
import sim.engine.entity.Operation
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.obj.GameObject

data class NPCOnObjectInteract(
    override val target: GameObject,
    override val option: String,
    val npc: NPC,
    var approachRange: Int? = null
) : InteractOption(npc, target, approachRange = approachRange) {
    override fun hasOperate() = Operation.npcObject.containsKey("$option:${npc.id}") || Operation.npcObject.containsKey("$option:*")

    override fun hasApproach() = Approachable.npcObject.containsKey("$option:${npc.id}") || Approachable.npcObject.containsKey("$option:*")

    override fun operate() {
        invoke(Operation.noDelays, Operation.npcObject)
    }

    override fun approach() {
        invoke(emptySet(), Approachable.npcObject)
    }

    private fun invoke(noDelays: Set<String>, map: Map<String, List<suspend NPC.(NPCOnObjectInteract) -> Unit>>) {
        Script.launch {
            val id = target.id
            if (!noDelays.contains("$option:$id") && (!noDelays.contains("$option:*") && !map.containsKey("$option:$id"))) {
                npc.arriveDelay()
            }
            for (block in map["$option:$id"] ?: map["$option:*"] ?: return@launch) {
                block(npc, this@NPCOnObjectInteract)
            }
        }
    }
}