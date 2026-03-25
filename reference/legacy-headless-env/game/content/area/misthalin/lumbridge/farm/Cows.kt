package content.area.misthalin.lumbridge.farm

import content.entity.effect.stun
import content.entity.player.dialogue.type.statement
import content.quest.questCompleted
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.mode.EmptyMode
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level
import sim.engine.entity.character.player.skill.level.Level.has
import sim.engine.inv.add
import sim.engine.inv.inventory
import sim.engine.timer.Timer
import sim.types.random

class Cows : Script {

    init {
        npcSpawn("cow_*") {
            softTimers.start("eat_grass")
        }

        npcTimerStart("eat_grass") {
            mode = EmptyMode
            random.nextInt(50, 200)
        }

        npcTimerTick("eat_grass") {
            if (mode == EmptyMode) {
                say("Moo")
                anim("cow_eat_grass")
            }
            Timer.CONTINUE
        }

        itemOnNPCOperate("*", "cow*") {
            message("The cow doesn't want that.")
        }

        objectOperate("Steal-cowbell", "dairy_cow") {
            if (!has(Skill.Thieving, 15)) {
                return@objectOperate
            }
            if (!questCompleted("cold_war")) {
                statement("You need to have started the Cold War quest to attempt this.")
                return@objectOperate
            }
            if (!Level.success(levels.get(Skill.Thieving), 128, 200)) {
                message("The cow kicks you and stuns you.")
                stun(this, 8, 10)
                return@objectOperate
            }
            inventory.add("cowbells")
        }
    }
}
