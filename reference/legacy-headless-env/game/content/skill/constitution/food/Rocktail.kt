package content.skill.constitution.food

import sim.engine.Script
import sim.engine.entity.character.player.skill.Skill

class Rocktail : Script {

    init {
        consumed("rocktail") { _, _ ->
            levels.boost(Skill.Constitution, 230, maximum = 100)
        }
    }
}
