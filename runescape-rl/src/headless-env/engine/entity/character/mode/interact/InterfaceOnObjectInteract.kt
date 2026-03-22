package sim.engine.entity.character.mode.interact

import sim.engine.Script
import sim.engine.entity.Approachable
import sim.engine.entity.Operation
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.name
import sim.engine.entity.obj.GameObject

data class InterfaceOnObjectInteract(
    override val target: GameObject,
    val id: String,
    val index: Int,
    val player: Player,
) : Interact(player, target) {
    override fun hasOperate() = Operation.onObject.containsKey("$id:*") || Operation.onObject.containsKey("$id:${target.def(player).stringId}") || Operation.onObject.containsKey("*:${target.def(player).stringId}")

    override fun hasApproach() = Approachable.onObject.containsKey("$id:*") || Approachable.onObject.containsKey("$id:${target.def(player).stringId}") || Approachable.onObject.containsKey("*:${target.def(player).stringId}")

    override fun operate() {
        invoke(Operation.onObject)
    }

    override fun approach() {
        invoke(Approachable.onObject)
    }

    private fun invoke(map: Map<String, List<suspend Player.(InterfaceOnObjectInteract) -> Unit>>) {
        Script.launch {
            for (block in map["$id:${target.def(player).stringId}"] ?: map["$id:*"] ?: map["*:${target.def(player).stringId}"] ?: return@launch) {
                block(player, this@InterfaceOnObjectInteract)
            }
        }
    }

    override fun toString(): String {
        return "${player.name} ${player.tile} - $id:${target.def(player).stringId} target=$target, interface='$id', index=$index"
    }

}