package content.skill.ranged.weapon.special

import content.entity.combat.hit.hit
import content.entity.proj.shoot
import sim.engine.Script
import sim.engine.entity.character.sound

class MagicLongbow : Script {

    init {
        specialAttack("powershot") { target, id ->
            anim("bow_accurate")
            gfx("special_arrow_shoot")
            sound("${id}_special")
            val time = shoot(id = "special_arrow", target = target)
            hit(target, delay = time)
        }
    }
}
