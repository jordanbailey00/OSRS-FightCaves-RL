package content.skill.constitution.food

import sim.engine.Script
import sim.engine.entity.character.player.skill.Skill

class HolyBiscuits : Script {

    init {
        consumed("holy_biscuits") { _, _ ->
            levels.restore(Skill.Prayer, 10)
        }
    }
}
