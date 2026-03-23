package content.skill.magic.book.ancient

import sim.engine.Script
import sim.engine.client.variable.start
import sim.engine.data.definition.SpellDefinitions
import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.combat.CombatAttack
import sim.engine.timer.epochSeconds

class MiasmicSpells(val definitions: SpellDefinitions) : Script {

    init {
        combatAttack("magic", handler = ::attack)
    }

    fun attack(source: Character, attack: CombatAttack) {
        val (target, damage, _, _, spell) = attack
        if (damage <= 0 || !spell.startsWith("miasmic_")) {
            return
        }
        val seconds: Int = definitions.get(spell)["effect_seconds"]
        target.start("miasmic", seconds, epochSeconds())
    }
}
