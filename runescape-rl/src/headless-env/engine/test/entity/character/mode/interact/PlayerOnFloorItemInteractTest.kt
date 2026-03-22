package sim.engine.entity.character.mode.interact

import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.floor.FloorItem
import sim.types.Tile

class PlayerOnFloorItemInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("option"),
    )
    
    override val failedChecks = listOf(
        listOf("*"),
    )

    override val operate: Script.(List<String>, Caller) -> Unit = { args, caller ->
        floorItemOperate(args[0]) {
            caller.call()
        }
    }

    override val approach: Script.(List<String>, Caller) -> Unit = { args, caller ->
        floorItemApproach(args[0]) {
            caller.call()
        }
    }

    override fun interact() = PlayerOnFloorItemInteract(FloorItem(Tile.EMPTY, "floor_item"), "option", Player(), null)

}