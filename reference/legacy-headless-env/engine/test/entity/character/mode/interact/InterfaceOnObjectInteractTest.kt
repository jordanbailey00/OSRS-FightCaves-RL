package sim.engine.entity.character.mode.interact

import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.player.Player
import sim.engine.entity.obj.GameObject

class InterfaceOnObjectInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("id", "obj"),
        listOf("*", "obj"),
        listOf("id", "*"),
    )
    
    override val operate: Script.(args: List<String>, caller: Caller) -> Unit = { args, caller ->
        onObjectOperate(args[0], args[1]) {
            caller.call()
        }
    }

    override val approach: Script.(args: List<String>, caller: Caller) -> Unit = { args, caller ->
        onObjectApproach(args[0], args[1]) {
            caller.call()
        }
    }

    override fun interact() = InterfaceOnObjectInteract(GameObject(0), "id", 0, Player())

}