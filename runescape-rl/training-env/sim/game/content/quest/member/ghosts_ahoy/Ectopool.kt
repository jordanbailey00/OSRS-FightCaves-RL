package content.quest.member.ghosts_ahoy

import content.entity.obj.ObjectTeleports
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.player.Teleport
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level.has
import sim.engine.get
import sim.engine.queue.queue
import sim.types.Direction

class Ectopool : Script {

    init {
        objTeleportTakeOff("Jump-down", "ectopool_shortcut_rail") { _, _ ->
            if (!has(Skill.Agility, 58)) {
                message("You need an agility level of at least 58 to climb down this wall.")
                return@objTeleportTakeOff Teleport.CANCEL
            }
            anim("jump_down")
            return@objTeleportTakeOff 1
        }

        objTeleportLand("Jump-down", "ectopool_shortcut_rail") { _, _ ->
            anim("jump_land")
        }

        objTeleportTakeOff("Jump-up", "ectopool_shortcut_wall") { target, option ->
            if (!has(Skill.Agility, 58)) {
                message("You need an agility level of at least 58 to climb up this wall.")
                return@objTeleportTakeOff Teleport.CANCEL
            }
            anim("jump_up")
            queue("jump_to") {
                val teleports = get<ObjectTeleports>()
                val definition = teleports.get(target.id, option).first()
                val tile = teleports.teleportTile(player, definition)
                tele(tile.addX(1))
                exactMoveDelay(tile, startDelay = 49, delay = 68, direction = Direction.WEST)
            }
            return@objTeleportTakeOff Teleport.CANCEL
        }
    }
}
