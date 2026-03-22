package content.skill.constitution.drink

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.item.Item

class ZamorakBrew : Script {

    init {
        consumable("zamorak_brew*,zamorak_mix*", ::consumable)
    }

    fun consumable(player: Player, item: Item): Boolean {
        val health = player.levels.get(Skill.Constitution)
        val damage = ((health / 100) * 10) + 20
        if (health - damage < 0) {
            player.message("You need more hitpoints in order to survive the effects of the zamorak brew.")
            return false
        }
        return true
    }
}
