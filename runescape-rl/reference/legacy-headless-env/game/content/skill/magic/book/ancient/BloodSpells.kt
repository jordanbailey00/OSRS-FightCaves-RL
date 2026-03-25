package content.skill.magic.book.ancient

import sim.engine.Script
import sim.engine.data.definition.SpellDefinitions
import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.combat.CombatAttack
import sim.engine.entity.character.player.skill.Skill

class BloodSpells(val definitions: SpellDefinitions) : Script {

    init {
        combatAttack("magic", handler = ::attack)
    }

    fun attack(source: Character, attack: CombatAttack) {
        val (_, damage, _, _, spell) = attack
        if (damage <= 0 || !spell.startsWith("blood_")) {
            return
        }
        val maxHeal: Int = definitions.get(spell)["max_heal"]
        val health = (damage / 4).coerceAtMost(maxHeal)
        source.levels.restore(Skill.Constitution, health)
    }
}
