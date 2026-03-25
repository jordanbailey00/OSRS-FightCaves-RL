package content.skill.melee.weapon.special

import sim.engine.Script
import sim.engine.entity.character.player.skill.Skill

class StatiusWarhammer : Script {

    init {
        specialAttackDamage("smash") { target, damage ->
            if (damage >= 0) {
                target.levels.drain(Skill.Defence, multiplier = 0.30)
            }
        }
    }
}
