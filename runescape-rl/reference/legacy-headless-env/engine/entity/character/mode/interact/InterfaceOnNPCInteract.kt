package sim.engine.entity.character.mode.interact

import sim.engine.Script
import sim.engine.entity.Approachable
import sim.engine.entity.Operation
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.name

data class InterfaceOnNPCInteract(
    override val target: NPC,
    val id: String,
    val index: Int,
    val player: Player,
) : Interact(player, target) {
    override fun hasOperate() = Operation.onNpc.containsKey("$id:*") || Operation.onNpc.containsKey("$id:${target.def(player).stringId}") || Operation.onNpc.containsKey("*:${target.def(player).stringId}")

    override fun hasApproach() = Approachable.onNpc.containsKey("$id:*") || Approachable.onNpc.containsKey("$id:${target.def(player).stringId}") || Approachable.onNpc.containsKey("*:${target.def(player).stringId}")

    override fun operate() {
        invoke(Operation.onNpc)
    }

    override fun approach() {
        invoke(Approachable.onNpc)
    }

    private fun invoke(map: Map<String, List<suspend Player.(InterfaceOnNPCInteract) -> Unit>>) {
        Script.launch {
            for (block in map["$id:${target.def(player).stringId}"] ?: map["$id:*"] ?: map["*:${target.def(player).stringId}"] ?: return@launch) {
                block(player, this@InterfaceOnNPCInteract)
            }
        }
    }

    override fun toString(): String {
        return "${player.name} ${player.tile} - $id:${target.def(player).stringId} target=$target, interface='$id', index=$index"
    }

}