package content.skill.constitution.drink

import sim.engine.Script
import sim.engine.entity.character.player.skill.Skill

class Cocktails : Script {

    init {
        consumed("wizard_blizzard") { _, _ ->
            levels.boost(Skill.Strength, 6)
            levels.drain(Skill.Attack, 4)
        }

        consumed("short_green_guy") { _, _ ->
            levels.boost(Skill.Strength, 4)
            levels.drain(Skill.Attack, 3)
        }

        consumed("drunk_dragon") { _, _ ->
            levels.boost(Skill.Strength, 5)
            levels.drain(Skill.Attack, 4)
        }

        consumed("chocolate_saturday") { _, _ ->
            levels.boost(Skill.Strength, 7)
            levels.drain(Skill.Attack, 4)
        }

        consumed("blurberry_special") { _, _ ->
            levels.boost(Skill.Strength, 6)
            levels.drain(Skill.Attack, 4)
        }
    }
}
