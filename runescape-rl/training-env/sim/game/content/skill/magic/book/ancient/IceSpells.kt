package content.skill.magic.book.ancient

import content.entity.effect.freeze
import sim.engine.Script
import sim.engine.data.definition.SpellDefinitions
import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.combat.CombatAttack

class IceSpells(val definitions: SpellDefinitions) : Script {

    init {
        combatAttack("magic", handler = ::attack)
    }

    fun attack(source: Character, attack: CombatAttack) {
        val (target, damage, _, _, spell) = attack
        if (damage <= 0 || !spell.startsWith("ice_")) {
            return
        }
        val ticks: Int = definitions.get(spell)["freeze_ticks"]
        source.freeze(target, ticks)
    }
}
