package sim.engine.entity.character.mode.interact

import sim.engine.Script
import sim.engine.entity.Approachable
import sim.engine.entity.Operation
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.name
import sim.engine.entity.item.Item
import sim.engine.entity.item.floor.FloorItem

data class ItemOnFloorItemInteract(
    override val target: FloorItem,
    val item: Item,
    val slot: Int,
    val id: String,
    val player: Player,
    val approachRange: Int?
) : Interact(player, target, approachRange = approachRange) {
    override fun hasOperate() = Operation.itemOnFloorItem.containsKey("${item.id}:*") || Operation.itemOnFloorItem.containsKey("${item.id}:${target.id}") || Operation.itemOnFloorItem.containsKey("*:${target.id}")

    override fun hasApproach() = Approachable.itemOnFloorItem.containsKey("${item.id}:*") || Approachable.itemOnFloorItem.containsKey("${item.id}:${target.id}") || Approachable.itemOnFloorItem.containsKey("*:${target.id}")

    override fun operate() {
        invoke(Operation.itemOnFloorItem)
    }

    override fun approach() {
        invoke(Approachable.itemOnFloorItem)
    }

    private fun invoke(map: Map<String, List<suspend Player.(ItemOnFloorItemInteract) -> Unit>>) {
        Script.launch {
            for (block in map["${item.id}:${target.id}"] ?: map["${item.id}:*"]  ?: map["*:${target.id}"] ?: return@launch) {
                block(player, this@ItemOnFloorItemInteract)
            }
        }
    }

    override fun toString(): String {
        return "${player.name} ${player.tile} - ${item.id}:${target.id} target=$target, item=$item, slot=$slot, interface='$id', approachRange=$approachRange"
    }

}