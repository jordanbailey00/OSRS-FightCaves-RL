package sim.engine.entity.character.mode.interact

import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item

class ItemOnPlayerInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("item"),
        listOf("*"),
        listOf("id"),
    )

    override val operate: Script.(List<String>, Caller) -> Unit = { args, caller ->
        itemOnPlayerOperate(args[0]) {
            caller.call()
        }
    }

    override val approach: Script.(List<String>, Caller) -> Unit = { args, caller ->
        itemOnPlayerApproach(args[0]) {
            caller.call()
        }
    }

    override fun interact() = ItemOnPlayerInteract(Player(0), "id", Item("item"), 0, Player(1))

}