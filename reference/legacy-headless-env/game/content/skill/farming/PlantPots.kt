package content.skill.farming

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.mode.interact.ItemOnObjectInteract
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.noInterest
import sim.engine.inv.inventory
import sim.engine.inv.replace
import sim.engine.queue.weakQueue

class PlantPots : Script {

    init {
        itemOnObjectOperate("plant_pot_empty", "*_patch_weeds_*") {
            message("This patch needs weeding first.")
        }
        itemOnObjectOperate("plant_pot_empty", "*_patch_weeded", handler = ::plantPot)
        itemOnItem("watering_can_0", "*_seedling") { _, _ ->
            message("You need to fill the watering can first.")
        }
    }

    private fun plantPot(player: Player, interact: ItemOnObjectInteract) {
        if (!player.inventory.contains("plant_pot_empty")) {
            return
        }
        if (!player.inventory.contains("gardening_trowel")) {
            player.message("You need a gardening trowel to do that.")
            return
        }
        val value = player[interact.target.id, "weeds_life3"]
        if (value != "weeds_0" && value.startsWith("weeds_3")) {
            player.message("This patch needs weeding first.")
            return
        } else if (value != "weeds_0") {
            player.noInterest()
            return
        }
        player.anim("farming_trowel_digging")
        player.weakQueue("fill_plant_pot", 2) {
            if (!player.inventory.replace("plant_pot_empty", "plant_pot")) {
                return@weakQueue
            }
            plantPot(player, interact)
        }
    }
}
