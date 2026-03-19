package content.skill.farming

import sim.engine.client.message
import sim.engine.data.Settings
import sim.engine.entity.character.player.Player
import sim.engine.inv.add
import sim.engine.inv.inventory
import sim.types.random

object ScrollOfLife {
    fun checkLife(player: Player, type: String, chop: Boolean = false) {
        if (!player["scroll_of_life", false]) {
            return
        }
        if (type == "calquat" || type == "spirit_tree") {
            return
        }
        if (random.nextInt(100) < if (type == "tree") 5 else 10) {
            player.inventory.add("${type}_seed") // TODO proper seed
            if (Settings["world.additional.messages", false]) {
                if (chop) {
                    player.message("<green>As the farmer chops the tree down, you spot a seed in the leftovers.")
                } else {
                    player.message("<green>Your Scroll of Life saves you a seed!")
                }
            }
//            player.message("The secret is yours! You read the scroll and unlock the long-lost technique of regaining seeds from dead farming patches.")
        }
    }
}
