package content.skill.melee.weapon.special

import content.entity.player.combat.special.SpecialAttack
import sim.engine.Script
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.sound

class DragonBattleaxe : Script {

    init {
        specialAttackPrepare("rampage") { id ->
            if (!SpecialAttack.drain(this)) {
                return@specialAttackPrepare false
            }
            anim("${id}_special")
            gfx("${id}_special")
            sound("${id}_special")
            say("Raarrrrrgggggghhhhhhh!")
            levels.drain(Skill.Attack, multiplier = 0.10)
            levels.drain(Skill.Defence, multiplier = 0.10)
            levels.drain(Skill.Magic, multiplier = 0.10)
            levels.drain(Skill.Ranged, multiplier = 0.10)
            levels.boost(Skill.Strength, amount = 5, multiplier = 0.15)
            return@specialAttackPrepare false
        }
    }
}
