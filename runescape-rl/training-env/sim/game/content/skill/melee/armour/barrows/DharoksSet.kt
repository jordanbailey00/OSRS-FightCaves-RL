package content.skill.melee.armour.barrows

import sim.engine.Script
import sim.engine.entity.character.player.Player
import sim.engine.inv.ItemAdded
import sim.engine.inv.ItemRemoved

class DharoksSet : Script {

    init {
        playerSpawn {
            if (hasFullSet()) {
                set("dharoks_set_effect", true)
            }
        }

        for (slot in BarrowsArmour.slots) {
            itemAdded("dharoks_*", "worn_equipment", slot, ::added)
            itemRemoved("dharoks_*", "worn_equipment", slot, ::removed)
        }
    }

    fun added(player: Player, update: ItemAdded) {
        if (player.hasFullSet()) {
            player["dharoks_set_effect"] = true
        }
    }

    fun removed(player: Player, update: ItemRemoved) {
        player.clear("dharoks_set_effect")
    }

    fun Player.hasFullSet() = BarrowsArmour.hasSet(
        this,
        "dharoks_greataxe",
        "dharoks_helm",
        "dharoks_platebody",
        "dharoks_platelegs",
    )
}
