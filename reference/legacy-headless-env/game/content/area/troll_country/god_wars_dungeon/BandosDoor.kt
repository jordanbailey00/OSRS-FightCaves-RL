package content.area.troll_country.god_wars_dungeon

import content.entity.obj.door.doorTarget
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level.has
import sim.engine.entity.obj.remove
import sim.engine.inv.inventory

class BandosDoor : Script {

    init {
        objectOperate("Bang", "godwars_bandos_big_door") { (target) ->
            if (tile.x >= target.tile.x) {
                if (!has(Skill.Strength, 70, message = true)) {
                    return@objectOperate
                }
                if (!inventory.contains("hammer")) {
                    message("You need a suitable hammer to ring the gong.")
                    return@objectOperate
                }
                anim("godwars_hammer_bang")
                delay(3)
            }
            target.remove(ticks = 2, collision = false)
            walkOverDelay(doorTarget(this, target) ?: return@objectOperate)
        }
    }
}
