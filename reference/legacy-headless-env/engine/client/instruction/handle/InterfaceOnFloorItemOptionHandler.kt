package sim.engine.client.instruction.handle

import com.github.michaelbull.logging.InlineLogger
import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.instruction.InterfaceHandler
import sim.engine.client.ui.closeInterfaces
import sim.engine.entity.character.mode.interact.InterfaceOnFloorItemInteract
import sim.engine.entity.character.mode.interact.ItemOnFloorItemInteract
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item
import sim.engine.entity.item.floor.FloorItem
import sim.engine.entity.item.floor.FloorItems
import sim.network.client.instruction.InteractInterfaceFloorItem

class InterfaceOnFloorItemOptionHandler(private val handler: InterfaceHandler) : InstructionHandler<InteractInterfaceFloorItem>() {

    private val logger = InlineLogger()

    override fun validate(player: Player, instruction: InteractInterfaceFloorItem): Boolean {
        val (floorItemId, x, y, interfaceId, componentId, itemId, itemSlot) = instruction
        val tile = player.tile.copy(x, y)
        val floorItem = FloorItems.at(tile).firstOrNull { it.def.id == floorItemId }
        if (floorItem == null) {
            logger.warn { "Invalid floor item $itemId $tile" }
            return false
        }
        val (id, component, item) = handler.getInterfaceItem(player, interfaceId, componentId, itemId, itemSlot) ?: return false
        player.closeInterfaces()
        if (item.isEmpty()) {
            player.interactOn(floorItem, id, component, itemSlot, approachRange = -1)
        } else {
            player.interactItemOn(floorItem, id, component, item, itemSlot, approachRange = -1)
        }
        return true
    }
}


fun Player.interactItemOn(target: FloorItem, id: String, component: String, item: Item = Item.EMPTY, itemSlot: Int = -1, approachRange: Int? = null) {
    mode = ItemOnFloorItemInteract(target, item, itemSlot, "$id:$component", this, approachRange)
}

fun Player.interactOn(target: FloorItem, id: String, component: String, index: Int = -1, approachRange: Int? = null) {
    mode = InterfaceOnFloorItemInteract(target, "$id:$component", index, this, approachRange)
}
