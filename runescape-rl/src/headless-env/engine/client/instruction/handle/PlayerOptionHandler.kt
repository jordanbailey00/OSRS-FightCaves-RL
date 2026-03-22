package sim.engine.client.instruction.handle

import com.github.michaelbull.logging.InlineLogger
import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.ui.closeInterfaces
import sim.engine.entity.character.mode.Follow
import sim.engine.entity.character.mode.interact.NPCOnPlayerInteract
import sim.engine.entity.character.mode.interact.PlayerOnPlayerInteract
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.PlayerOptions
import sim.engine.entity.character.player.Players
import sim.network.client.instruction.InteractPlayer

class PlayerOptionHandler : InstructionHandler<InteractPlayer>() {

    private val logger = InlineLogger()

    override fun validate(player: Player, instruction: InteractPlayer): Boolean {
        if (player.contains("delay")) {
            return false
        }
        val target = Players.indexed(instruction.playerIndex) ?: return false
        val optionIndex = instruction.option
        val option = player.options.get(optionIndex)
        if (option == PlayerOptions.EMPTY_OPTION) {
            logger.info { "Invalid player option $optionIndex ${player.options.get(optionIndex)} for $player on $target" }
            return false
        }
        player.closeInterfaces()
        if (option == "Follow") {
            player.mode = Follow(player, target)
        } else {
            player.interactPlayer(target, option)
        }
        return true
    }
}

fun Player.interactPlayer(target: Player, option: String) {
    mode = PlayerOnPlayerInteract(target, option, this)
}

fun NPC.interactPlayer(target: Player, option: String) {
    mode = NPCOnPlayerInteract(target, option, this)
}
