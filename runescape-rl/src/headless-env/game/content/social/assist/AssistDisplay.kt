package content.social.assist

import content.social.assist.Assistance.canAssist
import content.social.assist.Assistance.redirectSkillExperience
import content.social.assist.Assistance.stopRedirectingSkillExp
import net.pearx.kasechange.toSentenceCase
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.closeMenu
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill

class AssistDisplay : Script {

    init {
        interfaceOption(option = "Toggle Skill On / Off", id = "assist_xp:*") {
            val skill = Skill.valueOf(it.component.toSentenceCase())
            val assisted: Player? = get("assisted")
            if (assisted == null) {
                closeMenu()
            } else {
                blockSkillExperience(this, assisted, skill)
            }
        }
    }

    /**
     * Assistance system display interface
     */

    fun blockSkillExperience(player: Player, assisted: Player, skill: Skill) {
        val key = "assist_toggle_${skill.name.lowercase()}"
        if (!canAssist(player, assisted, skill)) {
            player[key] = false
            player.message("You can only assist skills which are higher than whom you are helping.")
        } else {
            if (player.toggle(key)) {
                redirectSkillExperience(assisted, skill)
            } else {
                stopRedirectingSkillExp(assisted, skill)
            }
        }
    }
}
