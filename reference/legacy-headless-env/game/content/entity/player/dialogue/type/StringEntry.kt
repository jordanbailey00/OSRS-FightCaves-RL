package content.entity.player.dialogue.type

import sim.engine.client.sendScript
import sim.engine.entity.character.player.Player
import sim.engine.suspend.StringSuspension

suspend fun Player.stringEntry(text: String): String {
    sendScript("string_entry", text)
    return StringSuspension.get(this)
}
