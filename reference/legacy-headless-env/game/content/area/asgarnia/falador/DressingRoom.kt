package content.area.asgarnia.falador

import sim.engine.Script
import sim.engine.client.ui.closeDialogue
import sim.engine.client.ui.closeMenu
import sim.engine.client.ui.open
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.flagAppearance
import sim.engine.timer.*

internal suspend fun Player.openDressingRoom(id: String) {
    closeDialogue()
    delay(1)
    gfx("dressing_room_start")
    delay(1)
    open(id)
    softTimers.start("dressing_room")
}

class DressingRoom : Script {

    init {
        timerStart("dressing_room") { 1 }

        timerTick("dressing_room") {
            gfx("dressing_room")
            Timer.CONTINUE
        }

        timerStop("dressing_room") {
            clearGfx()
            this["delay"] = 1
            closeMenu()
            gfx("dressing_room_finish")
            flagAppearance()
        }
    }
}
