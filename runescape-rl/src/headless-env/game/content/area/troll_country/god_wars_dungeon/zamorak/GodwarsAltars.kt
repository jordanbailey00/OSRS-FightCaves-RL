package content.area.troll_country.god_wars_dungeon.zamorak

import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.variable.hasClock
import sim.engine.client.variable.start
import sim.engine.data.definition.Areas
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.player.skill.Skill
import sim.engine.inv.equipment
import sim.engine.timer.toTicks
import java.util.concurrent.TimeUnit

class GodwarsAltars : Script {

    init {
        objectOperate("Pray-at", "prayer_*_altar_godwars") { (target) ->
            if (levels.getOffset(Skill.Prayer) >= 0) {
                message("You already have full Prayer points.")
                return@objectOperate
            }
            if (hasClock("godwars_altar_recharge")) {
                message("You must wait a total of 10 minutes before being able to recharge your prayer points.")
                return@objectOperate
            }
            if (hasClock("under_attack")) {
                message("You cannot recharge your prayer while engaged in combat.")
                return@objectOperate
            }

            val god = target.id.removePrefix("prayer_").removeSuffix("_altar_godwars")
            val bonus = equipment.items.count { it.def.getOrNull<String>("god") == god }
            levels.set(Skill.Prayer, levels.getMax(Skill.Prayer) + bonus)
            anim("altar_pray")
            message("Your prayer points feel rejuvenated.")
            start("godwars_altar_recharge", TimeUnit.MINUTES.toTicks(10))
        }

        objectOperate("Teleport", "prayer_*_altar_godwars") { (target) ->
            val god = target.id.removePrefix("prayer_").removeSuffix("_altar_godwars")
            tele(Areas["${god}_entrance"])
            visuals.hits.clear()
            message("The god's pity you and allow you to leave the encampment.")
        }
    }
}
