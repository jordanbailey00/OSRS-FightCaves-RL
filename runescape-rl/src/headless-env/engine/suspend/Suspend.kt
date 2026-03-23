package sim.engine.suspend

import sim.engine.client.ui.dialogue
import sim.engine.entity.character.Character
import sim.engine.entity.character.player.Player

fun Character.resumeSuspension(): Boolean {
    val suspend = suspension ?: return false
    if (suspend.ready()) {
        suspension = null
        suspend.resume()
    }
    return true
}

suspend fun Player.awaitDialogues(): Boolean {
    Suspension.start(this) { dialogue == null }
    return true
}
