package content.skill.magic.book.ancient

import content.skill.magic.spell.Spell
import sim.engine.Script
import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.combat.CombatAttack

class ShadowSpells : Script {

    init {
        combatAttack("magic", handler = ::attack)
    }

    fun attack(source: Character, attack: CombatAttack) {
        val (target, damage, _, _, spell) = attack
        if (damage <= 0 || !spell.startsWith("shadow_")) {
            return
        }
        Spell.drain(source, target, spell)
    }
}
