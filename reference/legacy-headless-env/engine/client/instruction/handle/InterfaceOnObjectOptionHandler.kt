package sim.engine.client.instruction.handle

import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.instruction.InterfaceHandler
import sim.engine.client.ui.closeInterfaces
import sim.engine.entity.character.mode.interact.InterfaceOnObjectInteract
import sim.engine.entity.character.mode.interact.ItemOnObjectInteract
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.noInterest
import sim.engine.entity.item.Item
import sim.engine.entity.obj.GameObject
import sim.engine.entity.obj.GameObjects
import sim.network.client.instruction.InteractInterfaceObject
import sim.types.Tile

class InterfaceOnObjectOptionHandler(
    private val handler: InterfaceHandler,
) : InstructionHandler<InteractInterfaceObject>() {

    override fun validate(player: Player, instruction: InteractInterfaceObject): Boolean {
        val (objectId, x, y, interfaceId, componentId, itemId, itemSlot) = instruction
        val tile = Tile(x, y, player.tile.level)
        val obj = GameObjects.findOrNull(tile, objectId)
        if (obj == null) {
            player.noInterest()
            return false
        }

        val (id, component, item) = handler.getInterfaceItem(player, interfaceId, componentId, itemId, itemSlot) ?: return false
        player.closeInterfaces()
        if (item.isEmpty()) {
            player.interactOn(obj, id, component, itemSlot)
        } else {
            player.interactItemOn(obj, id, component, item, itemSlot)
        }
        return true
    }
}

fun Player.interactItemOn(target: GameObject, id: String, component: String, item: Item = Item.EMPTY, itemSlot: Int = -1) {
    mode = ItemOnObjectInteract(target, item, itemSlot, "$id:$component", this)
}

fun Player.interactOn(target: GameObject, id: String, component: String, index: Int = -1) {
    mode = InterfaceOnObjectInteract(target, "$id:$component", index, this)
}