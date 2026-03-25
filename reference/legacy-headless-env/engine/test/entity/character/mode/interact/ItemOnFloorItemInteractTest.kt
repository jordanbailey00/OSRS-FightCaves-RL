package sim.engine.entity.character.mode.interact

import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item
import sim.engine.entity.item.floor.FloorItem
import sim.types.Tile

class ItemOnFloorItemInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("item", "floor_item"),
        listOf("item", "*"),
        listOf("*", "floor_item"),
    )

    override val operate: Script.(List<String>, Caller) -> Unit = { args, caller ->
        itemOnFloorItemOperate(args[0], args[1]) {
            caller.call()
        }
    }

    override val approach: Script.(List<String>, Caller) -> Unit = { args, caller ->
        itemOnFloorItemApproach(args[0], args[1]) {
            caller.call()
        }
    }

    override fun interact() = ItemOnFloorItemInteract(FloorItem(Tile.EMPTY, "floor_item"), Item("item"), 0, "id", Player(), null)

}