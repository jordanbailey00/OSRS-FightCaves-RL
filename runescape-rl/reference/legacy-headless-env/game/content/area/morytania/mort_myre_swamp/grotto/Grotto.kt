package content.area.morytania.mort_myre_swamp.grotto

import content.entity.combat.hit.damage
import content.entity.gfx.areaGfx
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.clearRenderEmote
import sim.engine.entity.character.player.renderEmote
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.exp.exp
import sim.engine.entity.character.player.skill.level.Level
import sim.engine.entity.character.sound
import sim.types.Direction
import sim.types.Tile
import sim.types.random

class Grotto : Script {

    init {
        objectOperate("Jump", "grotto_bridge") { (target) ->
            walkToDelay(target.tile)
            clear("face_entity")
            val direction = if (tile.y > 3330) Direction.SOUTH else Direction.NORTH
            if (Level.success(levels.get(Skill.Agility), 60..252)) {
                anim("stepping_stone_step", delay = 30)
                exactMoveDelay(target.tile.addY(direction.delta.y * 2), startDelay = 58, delay = 70, direction = direction)
                sound("jump")
                exp(Skill.Agility, 15.0)
            } else {
                anim("rope_walk_fall_${if (direction == Direction.SOUTH) "right" else "left"}")
                var river = Tile(3439, 3330)
                areaGfx("big_splash", tile, delay = 3)
                sound("pool_plop")
                exactMoveDelay(river, startDelay = 20, delay = 40, direction = Direction.WEST)
                renderEmote("swim")
                river = river.add(Direction.WEST)
                face(river)
                walkOverDelay(river)
                river = river.add(direction)
                face(river)
                walkOverDelay(river)
                delay()
                clearRenderEmote()
                walkOverDelay(river.add(direction))
                message("You nearly drown in the disgusting swamp.")
                damage(random.nextInt(70))
                exp(Skill.Agility, 2.0)
            }
        }
    }
}
