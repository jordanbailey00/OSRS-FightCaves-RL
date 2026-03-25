package content.skill.melee.weapon.special

import sim.engine.Script
import sim.engine.entity.character.player.skill.Skill

class DragonHatchet : Script {

    init {
        specialAttackDamage("clobber") { target, damage ->
            if (damage < 0) {
                return@specialAttackDamage
            }
            val drain = damage / 100
            if (drain > 0) {
                target.levels.drain(Skill.Defence, drain)
                target.levels.drain(Skill.Magic, drain)
            }
        }
    }
}
