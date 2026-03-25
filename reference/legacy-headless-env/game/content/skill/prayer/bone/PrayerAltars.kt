package content.skill.prayer.bone

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill

class PrayerAltars : Script {

    init {
        objectOperate("Pray", "prayer_altar_*") {
            pray()
        }

        objectOperate("Pray-at", "prayer_altar_*") {
            pray()
        }

        objectOperate("Check", "prayer_altar_chaos_varrock") {
            message("An altar to the evil god Zamorak.")
        }
    }

    fun Player.pray() {
        if (levels.getOffset(Skill.Prayer) >= 0) {
            message("You already have full Prayer points.")
        } else {
            levels.set(Skill.Prayer, levels.getMax(Skill.Prayer))
            anim("altar_pray")
            message("You recharge your Prayer points.")
            set("prayer_point_power_task", true)
        }
    }
}
