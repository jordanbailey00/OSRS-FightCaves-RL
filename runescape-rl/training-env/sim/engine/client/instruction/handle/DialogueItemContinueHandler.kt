package sim.engine.client.instruction.handle

import com.github.michaelbull.logging.InlineLogger
import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.ui.dialogue.Dialogues
import sim.engine.data.definition.ItemDefinitions
import sim.engine.entity.character.player.Player
import sim.network.client.instruction.InteractDialogueItem

class DialogueItemContinueHandler : InstructionHandler<InteractDialogueItem>() {

    private val logger = InlineLogger()

    override fun validate(player: Player, instruction: InteractDialogueItem): Boolean {
        val definition = ItemDefinitions.getOrNull(instruction.item)
        if (definition == null) {
            logger.debug { "Item ${instruction.item} not found for player $player." }
            return false
        }
        Dialogues.continueItem(player, definition.stringId)
        return true
    }
}
