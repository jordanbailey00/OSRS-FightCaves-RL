package sim.engine.entity.character.mode.interact

import sim.engine.Script
import sim.engine.entity.Approachable
import sim.engine.entity.Operation
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.name
import sim.engine.entity.item.Item
import sim.engine.entity.obj.GameObject

data class ItemOnObjectInteract(
    override val target: GameObject,
    val item: Item,
    val slot: Int,
    val id: String,
    val player: Player,
) : Interact(player, target) {
    override fun hasOperate() = Operation.itemOnObject.containsKey("${item.id}:*") || Operation.itemOnObject.containsKey("${item.id}:${target.def(player).stringId}") || Operation.itemOnObject.containsKey("*:${target.def(player).stringId}")

    override fun hasApproach() = Approachable.itemOnObject.containsKey("${item.id}:*") || Approachable.itemOnObject.containsKey("${item.id}:${target.def(player).stringId}") || Approachable.itemOnObject.containsKey("*:${target.def(player).stringId}")

    override fun operate() {
        invoke(Operation.noDelays, Operation.itemOnObject)
    }

    override fun approach() {
        invoke(emptySet(), Approachable.itemOnObject)
    }

    private fun invoke(noDelays: Set<String>, map: Map<String, List<suspend Player.(ItemOnObjectInteract) -> Unit>>) {
        Script.launch {
            if (!noDelays.contains("${item.id}:${target.def(player).stringId}")) {
                player.arriveDelay()
            }
            for (block in map["${item.id}:${target.def(player).stringId}"] ?: emptyList()) {
                block(player, this@ItemOnObjectInteract)
            }
            for (block in map["${item.id}:*"] ?: emptyList()) {
                block(player, this@ItemOnObjectInteract)
            }
            for (block in map["*:${target.def(player).stringId}"] ?: return@launch) {
                block(player, this@ItemOnObjectInteract)
            }
        }
    }

    override fun toString(): String {
        return "${player.name} ${player.tile} - ${item.id}:${target.def(player).stringId} target=$target, slot=$slot, interface='$id'"
    }

}