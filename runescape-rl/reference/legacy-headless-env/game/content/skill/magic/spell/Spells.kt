package content.skill.magic.spell

import content.area.wilderness.inMultiCombat
import content.entity.combat.hit.directHit
import content.skill.melee.weapon.multiTargets
import sim.engine.Script
import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.combat.CombatDamage
import sim.engine.entity.character.sound
import sim.types.random
import kotlin.random.nextInt

class Spells : Script {

    init {
        combatDamage(handler = ::damage)
        npcCombatDamage(handler = ::damage)

        combatAttack("magic") { (target, damage, type, weapon) ->
            if (!target.inMultiCombat) {
                return@combatAttack
            }
            if (spell.endsWith("_burst") || spell.endsWith("_barrage")) {
                val targets = multiTargets(target, 9)
                for (targ in targets) {
                    targ.directHit(this, random.nextInt(0..damage), type, weapon, spell)
                }
            }
        }
    }

    fun damage(character: Character, it: CombatDamage) {
        val spell = it.spell
        if (spell.isNotBlank()) {
            character.gfx("${spell}_impact")
            character.sound("${spell}_impact")
            it.source.sound("${spell}_impact")
        }
    }
}
