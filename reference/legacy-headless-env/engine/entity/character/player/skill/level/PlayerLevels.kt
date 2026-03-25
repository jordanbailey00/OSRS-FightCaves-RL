package sim.engine.entity.character.player.skill.level

import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.exp.Experience

class PlayerLevels(
    private val experience: Experience,
) : Levels.Level {

    override fun getMaxLevel(skill: Skill): Int {
        val exp = experience.get(skill)
        return Experience.level(skill, exp)
    }
}
