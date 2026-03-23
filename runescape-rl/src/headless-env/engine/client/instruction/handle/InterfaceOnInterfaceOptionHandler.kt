package sim.engine.client.instruction.handle

import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.instruction.InterfaceHandler
import sim.engine.client.ui.closeInterfaces
import sim.engine.client.ui.InterfaceApi
import sim.engine.entity.character.player.Player
import sim.network.client.instruction.InteractInterfaceItem

class InterfaceOnInterfaceOptionHandler(
    private val handler: InterfaceHandler,
) : InstructionHandler<InteractInterfaceItem>() {

    override fun validate(player: Player, instruction: InteractInterfaceItem): Boolean {
        val (fromItemId, toItemId, fromSlot, toSlot, fromInterfaceId, fromComponentId, toInterfaceId, toComponentId) = instruction

        val (fromId, fromComponent, fromItem) = handler.getInterfaceItem(player, fromInterfaceId, fromComponentId, fromItemId, fromSlot) ?: return false
        val (_, _, toItem) = handler.getInterfaceItem(player, toInterfaceId, toComponentId, toItemId, toSlot) ?: return false

        player.closeInterfaces()
        player.queue.clearWeak()
        player.suspension = null
        if (fromItem.isEmpty()) {
            InterfaceApi.onItem(player, "$fromId:$fromComponent", toItem)
        } else {
            InterfaceApi.itemOnItem(player, fromItem, toItem, fromSlot, toSlot)
        }
        return true
    }
}
