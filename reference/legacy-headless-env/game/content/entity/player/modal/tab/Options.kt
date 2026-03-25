package content.entity.player.modal.tab

import sim.engine.Script
import sim.engine.client.clearCamera
import sim.engine.client.message
import sim.engine.client.ui.hasMenuOpen
import sim.engine.client.ui.open

class Options : Script {

    init {
        playerSpawn {
            sendVariable("accept_aid")
        }

        interfaceOption("Graphics Settings", "options:graphics") {
            if (hasMenuOpen()) {
                message("Please close the interface you have open before setting your graphics options.")
                return@interfaceOption
            }
            clearCamera()
            open("graphics_options")
        }

        interfaceOption("Audio Settings", "options:audio") {
            if (hasMenuOpen()) {
                message("Please close the interface you have open before setting your audio options.")
                return@interfaceOption
            }
            open("audio_options")
        }

        interfaceOption("Toggle Accept Aid", "options:aid") {
            toggle("accept_aid")
        }
    }
}
