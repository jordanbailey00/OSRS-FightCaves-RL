package sim.engine.client.instruction.handle

import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.instruction.InterfaceHandler
import sim.engine.client.ui.InterfaceApi
import sim.engine.entity.character.player.Player
import sim.network.client.instruction.MoveInventoryItem

class InterfaceSwitchHandler(
    private val handler: InterfaceHandler,
) : InstructionHandler<MoveInventoryItem>() {

    override fun validate(player: Player, instruction: MoveInventoryItem): Boolean {
        var (fromInterfaceId, fromComponentId, fromItemId, fromSlot, toInterfaceId, toComponentId, toItemId, toSlot) = instruction
        if (toInterfaceId == 149) {
            toSlot -= 28
            val temp = fromItemId
            fromItemId = toItemId
            toItemId = temp
        }
        val (fromId, fromComponent) = handler.getInterfaceItem(player, fromInterfaceId, fromComponentId, fromItemId, fromSlot) ?: return false
        val (toId, toComponent) = handler.getInterfaceItem(player, toInterfaceId, toComponentId, toItemId, toSlot) ?: return false
        InterfaceApi.swap(player, "$fromId:$fromComponent", "$toId:$toComponent", fromSlot, toSlot)
        return true
    }
}
