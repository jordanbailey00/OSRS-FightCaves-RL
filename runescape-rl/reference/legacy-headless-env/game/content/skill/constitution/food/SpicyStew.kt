package content.skill.constitution.food

import sim.engine.Script
import sim.engine.entity.character.player.skill.Skill
import sim.types.random

class SpicyStew : Script {

    init {
        consumed("spicy_stew") { _, _ ->
            if (random.nextInt(100) > 5) {
                levels.boost(Skill.Cooking, 6)
            } else {
                levels.drain(Skill.Cooking, 6)
            }
        }
    }
}
