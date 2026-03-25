package content.skill.ranged.weapon.special

import content.entity.combat.hit.hit
import content.entity.proj.shoot
import sim.engine.Script
import sim.engine.entity.character.sound

class DorgeshuunCrossbow : Script {

    init {
        specialAttack("snipe") { target, id ->
            anim("crossbow_accurate")
            sound("${id}_special")
            val time = shoot(id = "snipe_special", target = target)
            hit(target, delay = time)
        }
    }
}
