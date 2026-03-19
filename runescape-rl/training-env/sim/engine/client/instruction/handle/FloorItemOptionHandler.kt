package sim.engine.client.instruction.handle

import com.github.michaelbull.logging.InlineLogger
import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.message
import sim.engine.client.ui.closeInterfaces
import sim.engine.entity.character.mode.interact.NPCOnFloorItemInteract
import sim.engine.entity.character.mode.interact.PlayerOnFloorItemInteract
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.item.floor.FloorItem
import sim.engine.entity.item.floor.FloorItems
import sim.network.client.instruction.InteractFloorItem

class FloorItemOptionHandler : InstructionHandler<InteractFloorItem>() {

    private val logger = InlineLogger()

    override fun validate(player: Player, instruction: InteractFloorItem): Boolean {
        if (player.contains("delay")) {
            return false
        }
        val (id, x, y, optionIndex) = instruction
        val tile = player.tile.copy(x, y)
        val floorItem = FloorItems.at(tile).firstOrNull { it.def.id == id }
        if (floorItem == null) {
            logger.warn { "Invalid floor item $id $tile" }
            return false
        }
        val options = floorItem.def.floorOptions
        val selectedOption = options.getOrNull(optionIndex)
        if (selectedOption == null) {
            logger.warn { "Invalid floor item option $optionIndex ${options.contentToString()}" }
            return false
        }
        if (selectedOption == "Examine") {
            player.message(floorItem.def.getOrNull("examine") ?: return false, ChatType.ItemExamine)
            return false
        }
        player.closeInterfaces()
        player.interactFloorItem(floorItem, selectedOption, -1)
        return true
    }
}

fun Player.interactFloorItem(target: FloorItem, option: String, shape: Int? = null) {
    mode = PlayerOnFloorItemInteract(target, option, this, shape)
}

fun NPC.interactFloorItem(target: FloorItem, option: String, shape: Int? = null) {
    mode = NPCOnFloorItemInteract(target, option, this, shape)
}
