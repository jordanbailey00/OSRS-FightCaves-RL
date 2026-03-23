package content.area.wilderness

import content.skill.melee.weapon.weapon
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.mode.interact.PlayerOnObjectInteract
import sim.engine.entity.character.player.Player
import sim.engine.entity.obj.GameObject
import sim.engine.entity.obj.replace
import sim.engine.timer.toTicks
import java.util.concurrent.TimeUnit

class Web : Script {

    init {
        objectOperate("Pass", "web_spider", handler = ::slash)
        objectOperate("Slash", "web", handler = ::slash)
        itemOnObjectOperate(obj = "web*") { (target, item) ->
            if (item.id == "knife" || item.def["slash_attack", 0] > 0) {
                message("Only a sharp blade can cut through this sticky web.")
                return@itemOnObjectOperate
            }
            slash(this, target)
        }
    }

    fun slash(player: Player, interact: PlayerOnObjectInteract) {
        if (player.weapon.def["slash_attack", 0] <= 0) {
            player.message("Only a sharp blade can cut through this sticky web.")
            return
        }
        slash(player, interact.target)
    }

    fun slash(player: Player, target: GameObject) {
        player.anim("dagger_slash")
        target.replace("web_slashed", ticks = TimeUnit.MINUTES.toTicks(1))
    }
}
