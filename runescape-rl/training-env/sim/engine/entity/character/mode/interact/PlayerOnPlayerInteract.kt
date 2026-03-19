package sim.engine.entity.character.mode.interact

import sim.engine.Script
import sim.engine.entity.Approachable
import sim.engine.entity.Operation
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.name

data class PlayerOnPlayerInteract(
    override val target: Player,
    override val option: String,
    val player: Player,
) : InteractOption(player, target) {
    override fun hasOperate() = Operation.playerPlayer.containsKey(option)

    override fun hasApproach() = Approachable.playerPlayer.containsKey(option)

    override fun operate() {
        invoke(Operation.playerPlayer)
    }

    override fun approach() {
        invoke(Approachable.playerPlayer)
    }

    private fun invoke(map: Map<String, List<suspend Player.(PlayerOnPlayerInteract) -> Unit>>) {
        Script.launch {
            for (block in map[option] ?: return@launch) {
                block(player, this@PlayerOnPlayerInteract)
            }
        }
    }

    override fun toString(): String {
        return "${player.name} ${player.tile} - $option target=$target"
    }

}