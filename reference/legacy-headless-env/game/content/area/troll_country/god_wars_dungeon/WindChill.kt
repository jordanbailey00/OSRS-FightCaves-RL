package content.area.troll_country.god_wars_dungeon

import content.entity.combat.hit.directHit
import content.entity.player.effect.energy.runEnergy
import sim.engine.Script
import sim.engine.client.ui.close
import sim.engine.client.ui.open
import sim.engine.data.definition.Areas
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.sound
import sim.engine.inv.inventory
import sim.engine.timer.*

class WindChill : Script {

    init {
        timerStart("windchill") {
            open("snow_flakes")
            10
        }

        timerTick("windchill") {
            if (tile !in Areas["godwars_chill_area"]) {
                return@timerTick Timer.CANCEL
            }
            sound("windy")
            runEnergy = 0
            for (skill in Skill.all) {
                if (skill == Skill.Constitution) {
                    if (levels.get(Skill.Constitution) > 10) {
                        directHit(10)
                    }
                    continue
                }
                levels.drain(skill, 1)
            }
            return@timerTick Timer.CONTINUE
        }

        timerStop("windchill") {
            close("snow_flakes")
        }

        entered("godwars_chill_area") {
            sendVariable("godwars_knights_notes")
            timers.start("windchill")
        }

        exited("godwars_chill_area") {
            if (inventory.contains("knights_notes") || inventory.contains("knights_notes_opened")) {
                set("godwars_knights_notes", true)
            }
            timers.stop("windchill")
        }
    }
}
