package content.entity.npc.combat.melee

import sim.engine.Script
import sim.engine.entity.character.areaSound
import sim.engine.entity.item.floor.FloorItems
import sim.engine.timer.Timer
import sim.types.random

class Chicken : Script {

    init {
        npcSpawn("chicken*") {
            areaSound("chicken_defend", tile)
            say("squawk!")
            softTimers.start("lay_eggs")
        }
        npcTimerStart("lay_eggs") {
            // Do not have authentic data. Used this so no spamming eggs all the time.
            random.nextInt(200, 400)
        }
        npcTimerTick("lay_eggs") {
            anim("chicken_defend")
            // Timed on Rs3 but can be inauthentic for 2011
            FloorItems.add(tile, "egg", disappearTicks = 100)
            say("squawk!")
            areaSound("lay_eggs", tile)
            Timer.CONTINUE
        }
    }
}
