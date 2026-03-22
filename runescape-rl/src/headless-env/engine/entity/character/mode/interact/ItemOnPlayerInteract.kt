package sim.engine.entity.character.mode.interact

import sim.engine.Script
import sim.engine.entity.Approachable
import sim.engine.entity.Operation
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.name
import sim.engine.entity.item.Item

data class ItemOnPlayerInteract(
    override val target: Player,
    val id: String,
    val item: Item,
    val slot: Int,
    val player: Player,
) : Interact(player, target) {
    override fun hasOperate() = Operation.onPlayer.containsKey(id) || Operation.onPlayer.containsKey(item.id) || Operation.onPlayer.containsKey("*")

    override fun hasApproach() = Approachable.onPlayer.containsKey(id) || Approachable.onPlayer.containsKey(item.id) || Approachable.onPlayer.containsKey("*")

    override fun operate() {
        invoke(Operation.onPlayer)
    }

    override fun approach() {
        invoke(Approachable.onPlayer)
    }

    private fun invoke(map: Map<String, List<suspend Player.(ItemOnPlayerInteract) -> Unit>>) {
        Script.launch {
            for (block in map[id] ?: map[item.id] ?: map["*"] ?: return@launch) {
                block(player, this@ItemOnPlayerInteract)
            }
        }
    }

    override fun toString(): String {
        return "${player.name} ${player.tile} - target=$target, id='$id', item=$item, slot=$slot"
    }

}