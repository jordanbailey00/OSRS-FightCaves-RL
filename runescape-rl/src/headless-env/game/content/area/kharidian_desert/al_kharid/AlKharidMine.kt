package content.area.kharidian_desert.al_kharid

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.clearRenderEmote
import sim.engine.entity.character.player.renderEmote
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level.has
import sim.types.Direction
import sim.types.Tile

class AlKharidMine : Script {

    init {
        objectOperate("Climb", "al_kharid_mine_shortcut_bottom") {
            if (!has(Skill.Agility, 38)) {
                message("You need an Agility level of 38 to negotiate these rocks.")
                return@objectOperate
            }
            face(Direction.EAST)
            delay()
            walkToDelay(Tile(3303, 3315))
            renderEmote("climbing")
            walkOverDelay(Tile(3307, 3315))
            clearRenderEmote()
        }

        objectOperate("Climb", "al_kharid_mine_shortcut_top") {
            if (!has(Skill.Agility, 38)) {
                message("You need an Agility level of 38 to negotiate these rocks.")
                return@objectOperate
            }
            face(Direction.EAST)
            delay()
            walkOverDelay(Tile(3305, 3315))
            face(Direction.WEST)
            delay()
            anim("human_climbing_down", delay = 10)
            exactMoveDelay(Tile(3303, 3315), delay = 120, direction = Direction.EAST)
        }
    }
}
