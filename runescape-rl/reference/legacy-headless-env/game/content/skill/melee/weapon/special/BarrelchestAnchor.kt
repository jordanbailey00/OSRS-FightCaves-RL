package content.skill.melee.weapon.special

import content.entity.combat.hit.hit
import content.skill.melee.weapon.drainByDamage
import sim.engine.Script
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.sound

class BarrelchestAnchor : Script {

    init {
        specialAttack("sunder") { target, id ->
            anim("${id}_special")
            gfx("${id}_special")
            sound("${id}_special")
            val damage = hit(target, delay = 60)
            if (damage >= 0) {
                drainByDamage(target, damage, Skill.Defence, Skill.Strength, Skill.Prayer, Skill.Attack, Skill.Magic, Skill.Ranged)
            }
        }
    }
}
