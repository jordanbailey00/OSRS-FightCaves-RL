package sim.engine.entity.character.mode.interact

import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.floor.FloorItem
import sim.types.Tile

class InterfaceOnFloorItemInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("id", "floor_item"),
        listOf("*", "floor_item"),
        listOf("id", "*"),
    )

    override val operate: Script.(args: List<String>, caller: Caller) -> Unit = { args, caller ->
        onFloorItemOperate(args[0], args[1]) {
            caller.call()
        }
    }

    override val approach: Script.(args: List<String>, caller: Caller) -> Unit = { args, caller ->
        onFloorItemApproach(args[0], args[1]) {
            caller.call()
        }
    }

    override fun interact() = InterfaceOnFloorItemInteract(FloorItem(Tile.EMPTY, "floor_item"), "id", 0, Player(), null)

}