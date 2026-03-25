package sim.engine.entity.character.mode.interact

import sim.engine.Script
import sim.engine.entity.Approachable
import sim.engine.entity.Operation
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.name

data class PlayerOnNPCInteract(
    override val target: NPC,
    override val option: String,
    val player: Player,
) : InteractOption(player, target) {
    override fun hasOperate() = Operation.playerNpc.containsKey("$option:${target.def(player).stringId}") || Operation.playerNpc.containsKey("$option:*")

    override fun hasApproach() = Approachable.playerNpc.containsKey("$option:${target.def(player).stringId}") || Approachable.playerNpc.containsKey("$option:*")

    override fun operate() {
        invoke(Operation.playerNpc)
    }

    override fun approach() {
        invoke(Approachable.playerNpc)
    }

    private fun invoke(map: Map<String, List<suspend Player.(PlayerOnNPCInteract) -> Unit>>) {
        Script.launch {
            for (block in map["$option:${target.def(player).stringId}"] ?: map["$option:*"] ?: return@launch) {
                block(player, this@PlayerOnNPCInteract)
            }
        }
    }

    override fun toString(): String {
        return "${player.name} ${player.tile} - $option:${target.def(player).stringId} target=$target"
    }

}