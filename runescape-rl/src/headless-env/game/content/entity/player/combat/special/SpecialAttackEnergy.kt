package content.entity.player.combat.special

import sim.engine.Script
import sim.engine.client.message
import sim.engine.timer.Timer
import kotlin.math.min

class SpecialAttackEnergy : Script {

    val half = MAX_SPECIAL_ATTACK / 2
    val tenth = MAX_SPECIAL_ATTACK / 10

    init {
        playerSpawn {
            if (specialAttackEnergy < MAX_SPECIAL_ATTACK) {
                softTimers.start("restore_special_energy")
            }
        }
        timerStart("restore_special_energy") { 50 }

        timerTick("restore_special_energy") {
            val energy = specialAttackEnergy
            if (energy >= MAX_SPECIAL_ATTACK) {
                return@timerTick Timer.CANCEL
            }
            val restore = min(tenth, MAX_SPECIAL_ATTACK - energy)
            specialAttackEnergy += restore
            if (specialAttackEnergy.rem(half) == 0) {
                message("Your special attack energy is now ${if (specialAttackEnergy == MAX_SPECIAL_ATTACK) 100 else 50}%.")
            }
            return@timerTick Timer.CONTINUE
        }
    }
}
