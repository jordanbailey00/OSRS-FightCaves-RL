package content.social.assist

import content.social.assist.Assistance.getHoursRemaining
import content.social.assist.Assistance.hasEarnedMaximumExperience
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.chat.plural
import sim.engine.client.ui.closeMenu
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.ChatType

class AssistFilter : Script {

    init {
        interfaceOption("XP Earned/Time", "filter_buttons:assist") {
            if (hasEarnedMaximumExperience(this)) {
                val hours = getHoursRemaining(this)
                message(
                    "You've earned the maximum XP (30,000 Xp) from the Assist System within a 24-hour period.",
                    ChatType.Assist,
                )
                message("You can assist again in $hours ${"hour".plural(hours)}.", ChatType.Assist)
            } else {
                val earned = get("total_xp_earned", 0.0)
                message("You have earned $earned Xp. The Assist system is available to you.", ChatType.Assist)
            }
        }

        interfaceOption("On Assist", "filter_buttons:assist") {
            set("assist_status", "on")
        }

        interfaceOption("Friends Assist", "filter_buttons:assist") {
            set("assist_status", "friends")
            cancel()
        }

        interfaceOption("Off Assist", "filter_buttons:assist") {
            set("assist_status", "off")
            cancel()
        }
    }

    /**
     * Assistance privacy filter settings
     */

    fun Player.cancel() {
        if (contains("assistant")) {
            val assistant: Player? = get("assistant")
            assistant?.closeMenu()
        }

        if (contains("assisted")) {
            closeMenu()
        }
    }
}
