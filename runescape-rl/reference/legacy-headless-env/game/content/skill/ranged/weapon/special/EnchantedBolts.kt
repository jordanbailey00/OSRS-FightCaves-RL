package content.skill.ranged.weapon.special

import sim.engine.Script
import sim.engine.client.variable.hasClock
import sim.engine.entity.character.player.skill.Skill

class EnchantedBolts : Script {

    init {
        combatAttack("range") { (_, damage) ->
            if (!hasClock("life_leech") || damage < 4) {
                return@combatAttack
            }
            levels.restore(Skill.Constitution, damage / 4)
        }
    }
}
