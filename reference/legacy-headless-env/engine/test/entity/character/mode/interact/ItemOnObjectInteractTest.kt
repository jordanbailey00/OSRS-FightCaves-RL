package sim.engine.entity.character.mode.interact

import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item
import sim.engine.entity.obj.GameObject

class ItemOnObjectInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("item", "obj"),
        listOf("item", "*"),
        listOf("*", "obj"),
    )

    override val operate: Script.(List<String>, Caller) -> Unit = { args, caller ->
        itemOnObjectOperate(args[0], args[1]) {
            caller.call()
        }
    }

    override val approach: Script.(List<String>, Caller) -> Unit = { args, caller ->
        itemOnObjectApproach(args[0], args[1]) {
            caller.call()
        }
    }

    override fun interact() = ItemOnObjectInteract(GameObject(0), Item("item"), 0, "id", Player())

}