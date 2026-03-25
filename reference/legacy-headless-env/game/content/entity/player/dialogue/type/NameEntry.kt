package content.entity.player.dialogue.type

import sim.engine.client.sendScript
import sim.engine.entity.character.player.Player
import sim.engine.suspend.NameSuspension

suspend fun Player.nameEntry(text: String): String {
    sendScript("name_entry", text)
    return NameSuspension.get(this)
}
