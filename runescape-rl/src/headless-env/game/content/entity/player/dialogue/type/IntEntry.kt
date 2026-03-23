package content.entity.player.dialogue.type

import sim.engine.client.sendScript
import sim.engine.entity.character.player.Player
import sim.engine.suspend.IntSuspension

suspend fun Player.intEntry(text: String): Int {
    sendScript("int_entry", text)
    return IntSuspension.get(this)
}
