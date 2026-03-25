package content.entity.player.effect.energy

import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.sendRunEnergy
import sim.engine.entity.character.mode.EmptyMode
import sim.engine.entity.character.mode.Rest
import sim.engine.entity.character.move.running
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.ChatType

class Running : Script {

    init {
        playerSpawn {
            sendVariable("movement")
        }

        interfaceOpened("energy_orb") {
            sendRunEnergy(energyPercent())
        }

        interfaceOption(option = "Turn Run mode on", id = "energy_orb:*") {
            if (mode is Rest) {
                val walking = get("movement", "walk") == "walk"
                toggleRun(this, !walking)
                set("movement_temp", if (walking) "run" else "walk")
                mode = EmptyMode
                return@interfaceOption
            }
            toggleRun(this, running)
        }
    }

    fun toggleRun(player: Player, run: Boolean) {
        val energy = player.energyPercent()
        if (energy == 0) {
            player.message("You don't have enough energy left to run!", ChatType.Filter)
        }
        player.running = !run && energy > 0
    }
}
