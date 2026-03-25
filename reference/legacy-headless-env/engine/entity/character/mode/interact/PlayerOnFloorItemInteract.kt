package sim.engine.entity.character.mode.interact

import sim.engine.Script
import sim.engine.entity.Approachable
import sim.engine.entity.Operation
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.name
import sim.engine.entity.item.floor.FloorItem

data class PlayerOnFloorItemInteract(
    override val target: FloorItem,
    override val option: String,
    val player: Player,
    val shape: Int?
) : InteractOption(player, target, shape = shape) {
    override fun hasOperate() = Operation.playerFloorItem.containsKey(option)

    override fun hasApproach() = Approachable.playerFloorItem.containsKey(option)

    override fun operate() {
        invoke(Operation.playerFloorItem)
    }

    override fun approach() {
        invoke(Approachable.playerFloorItem)
    }

    private fun invoke(map: Map<String, List<suspend Player.(PlayerOnFloorItemInteract) -> Unit>>) {
        Script.launch {
            for (block in map[option] ?: return@launch) {
                block(player, this@PlayerOnFloorItemInteract)
            }
        }
    }

    override fun toString(): String {
        return "${player.name} ${player.tile} - $option target=$target, shape=$shape"
    }

}