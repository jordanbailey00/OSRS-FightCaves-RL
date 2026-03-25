package sim.engine.entity.character.mode.interact

import sim.engine.Script
import sim.engine.entity.Approachable
import sim.engine.entity.Operation
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player

data class NPCOnPlayerInteract(
    override val target: Player,
    override val option: String,
    val npc: NPC,
) : InteractOption(npc, target) {
    override fun hasOperate() = Operation.npcPlayer.containsKey(option)

    override fun hasApproach() = Approachable.npcPlayer.containsKey(option)

    override fun operate() {
        invoke(Operation.npcPlayer)
    }

    override fun approach() {
        invoke(Approachable.npcPlayer)
    }

    private fun invoke(map: Map<String, List<suspend NPC.(NPCOnPlayerInteract) -> Unit>>) {
        Script.launch {
            for (block in map[option] ?: return@launch) {
                block(npc, this@NPCOnPlayerInteract)
            }
        }
    }
}