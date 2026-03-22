package sim.engine.client.instruction.handle

import com.github.michaelbull.logging.InlineLogger
import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.instruction.handle.ObjectOptionHandler.Companion.getDefinition
import sim.engine.client.message
import sim.engine.client.ui.closeInterfaces
import sim.engine.client.ui.dialogue.talkWith
import sim.engine.client.variable.hasClock
import sim.engine.data.definition.NPCDefinitions
import sim.engine.entity.character.mode.interact.NPCOnNPCInteract
import sim.engine.entity.character.mode.interact.PlayerOnNPCInteract
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.chat.noInterest
import sim.network.client.instruction.InteractNPC

class NPCOptionHandler : InstructionHandler<InteractNPC>() {

    private val logger = InlineLogger()

    override fun validate(player: Player, instruction: InteractNPC): Boolean {
        if (player.contains("delay")) {
            return false
        }
        val npc = NPCs.indexed(instruction.npcIndex) ?: return false
        var def = npc.def
        val transform = npc["transform_id", ""]
        if (transform.isNotBlank()) {
            def = NPCDefinitions.get(transform)
        }
        val definition = getDefinition(player, NPCDefinitions, def, def)
        val options = definition.options
        val index = instruction.option - 1
        val selectedOption = options.getOrNull(index)
        if (selectedOption == null) {
            player.noInterest()
            logger.warn { "Invalid npc option $npc $index ${options.contentToString()}" }
            return false
        }
        if (selectedOption == "Listen-to" && player["movement", "walk"] == "music") {
            player.message("You are already resting.")
            return false
        }
        if (player.hasClock("stunned")) {
            player.message("You're stunned!", ChatType.Filter)
            return false
        }
        player.closeInterfaces()
        player.talkWith(npc, definition)
        player.interactNpc(npc, selectedOption)
        return true
    }
}

fun Player.interactNpc(target: NPC, option: String) {
    mode = PlayerOnNPCInteract(target, option, this)
}

fun NPC.interactNpc(target: NPC, option: String) {
    mode = NPCOnNPCInteract(target, option, this)
}
