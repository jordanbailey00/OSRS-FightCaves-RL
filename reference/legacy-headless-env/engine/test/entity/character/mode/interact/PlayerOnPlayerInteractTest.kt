package sim.engine.entity.character.mode.interact

import sim.engine.Caller
import sim.engine.Script
import sim.engine.entity.character.player.Player

class PlayerOnPlayerInteractTest : OnInteractTest() {

    override val checks = listOf(
        listOf("option"),
    )
    
    override val failedChecks = listOf(
        listOf("*"),
    )

    override val operate: Script.(List<String>, Caller) -> Unit = { args, caller ->
        playerOperate(args[0]) {
            caller.call()
        }
    }

    override val approach: Script.(List<String>, Caller) -> Unit = { args, caller ->
        playerApproach(args[0]) {
            caller.call()
        }
    }

    override fun interact() = PlayerOnPlayerInteract(Player(1), "option", Player(2))

}