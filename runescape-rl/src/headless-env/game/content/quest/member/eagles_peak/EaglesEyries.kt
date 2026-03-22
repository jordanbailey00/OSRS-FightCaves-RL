package content.quest.member.eagles_peak

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.clearRenderEmote
import sim.engine.entity.character.player.renderEmote
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.exp.exp
import sim.engine.entity.character.player.skill.level.Level.has
import sim.types.Direction
import sim.types.Tile
import sim.types.equals

class EaglesEyries : Script {

    init {
        objectOperate("Climb", "eagles_peak_rocks") { (target) ->
            if (!has(Skill.Agility, 25)) {
                message("You must have an Agility level of at least 25 to climb these rocks.")
                return@objectOperate
            }
            val up = target.tile.equals(2322, 3501)
            if (up) {
                anim("rocks_climb", delay = 15)
                exactMove(Tile(2324, 3497), 250, Direction.SOUTH)
                delay(7)
                anim("human_walk_forward")
                delay()
            } else {
                anim("human_walk_backwards", delay = 7)
                exactMove(Tile(2322, 3502), 240, Direction.SOUTH)
                delay()
                anim("rocks_climb_down")
                delay(7)
            }
            clearAnim()
            exp(Skill.Agility, 1.0)
        }

        objectOperate("Climb", "rocky_handholds_bottom") {
            if (!has(Skill.Agility, 35)) {
                message("You must have an Agility level of at least 35 to climb these rocks.")
                return@objectOperate
            }
            delay()
            renderEmote("climbing")
            walkOverDelay(Tile(2744, 3830, 1))
            clearRenderEmote()
            exp(Skill.Agility, 1.0)
        }

        objectOperate("Climb", "rocky_handholds_top") {
            if (!has(Skill.Agility, 35)) {
                message("You must have an Agility level of at least 35 to climb these rocks.")
                return@objectOperate
            }
            anim("rocks_climb_down", delay = 15)
            exactMoveDelay(Tile(2740, 3830, 1), 90, Direction.EAST)
            clearAnim()
            exp(Skill.Agility, 1.0)
        }
    }
}
