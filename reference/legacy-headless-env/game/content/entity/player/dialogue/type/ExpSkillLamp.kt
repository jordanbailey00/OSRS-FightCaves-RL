package content.entity.player.dialogue.type

import net.pearx.kasechange.toPascalCase
import sim.engine.client.ui.close
import sim.engine.client.ui.open
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.suspend.StringSuspension

private const val EXPERIENCE_SKILL_LAMP = "skill_stat_advance"

suspend fun Player.skillLamp(): Skill {
    check(open(EXPERIENCE_SKILL_LAMP)) { "Unable to open skill lamp dialogue for $this" }
    val result = StringSuspension.get(this)
    close(EXPERIENCE_SKILL_LAMP)
    return Skill.valueOf(result.toPascalCase())
}
