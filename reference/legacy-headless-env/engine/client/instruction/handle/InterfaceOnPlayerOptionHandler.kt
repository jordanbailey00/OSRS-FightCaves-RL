package sim.engine.client.instruction.handle

import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.instruction.InterfaceHandler
import sim.engine.client.ui.closeInterfaces
import sim.engine.entity.character.mode.interact.ItemOnPlayerInteract
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.network.client.instruction.InteractInterfacePlayer

class InterfaceOnPlayerOptionHandler(
    private val handler: InterfaceHandler,
) : InstructionHandler<InteractInterfacePlayer>() {

    override fun validate(player: Player, instruction: InteractInterfacePlayer): Boolean {
        val (playerIndex, interfaceId, componentId, itemId, itemSlot) = instruction
        val target = Players.indexed(playerIndex) ?: return false

        val (id, component, item) = handler.getInterfaceItem(player, interfaceId, componentId, itemId, itemSlot) ?: return false
        player.closeInterfaces()
        player.mode = ItemOnPlayerInteract(target, "$id:$component", item, itemSlot, player)
        return true
    }
}
