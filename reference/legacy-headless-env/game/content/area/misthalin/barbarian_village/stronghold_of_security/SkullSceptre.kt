package content.area.misthalin.barbarian_village.stronghold_of_security

import com.github.michaelbull.logging.InlineLogger
import content.entity.player.dialogue.type.item
import sim.engine.Script
import sim.engine.client.ui.chat.plural
import sim.engine.entity.character.player.Teleport
import sim.engine.entity.character.player.equip.equipped
import sim.engine.inv.charges
import sim.engine.inv.discharge
import sim.engine.inv.equipment
import sim.engine.inv.inventory
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.types.Tile

class SkullSceptre : Script {

    val logger = InlineLogger()

    init {
        crafted {
            if (it.add.any { item -> item.id == "strange_skull" }) {
                item("strange_skull", 600, "The two halves of the skull fit perfectly, they appear to have a fixing point, perhaps they are to be mounted on something?")
            } else if (it.add.any { item -> item.id == "runed_sceptre" }) {
                item("runed_sceptre", 900, "The two halves of the sceptre fit perfectly. It appears to be designed to have something on top.")
            } else if (it.add.any { item -> item.id == "skull_sceptre" }) {
                item("skull_sceptre", 900, "The skull fits perfectly atop the sceptre. You feel there is great magical power at work here, and that the sceptre has 10 charges.")
            }
        }

        itemOption("Invoke", "skull_sceptre", "*") {
            Teleport.teleport(this, Tile(3081, 3421), "skull_sceptre")
        }

        teleportTakeOff("skull_sceptre") {
            if (equipped(EquipSlot.Weapon).id == "skull_sceptre") {
                if (!equipment.discharge(this, EquipSlot.Weapon.index, 1)) {
                    logger.warn { "Failed to discharge skull sceptre for $this" }
                    return@teleportTakeOff false
                }
                return@teleportTakeOff true
            }
            val index = inventory.indexOf("skull_sceptre")
            if (index == -1) {
                logger.warn { "Failed to find skull sceptre for $this" }
                return@teleportTakeOff false
            }
            if (!inventory.discharge(this, index, 1)) {
                logger.warn { "Failed to discharge skull sceptre for $this" }
                return@teleportTakeOff false
            }
            return@teleportTakeOff true
        }

        itemOption("Divine", "skull_sceptre") { (item) ->
            val charges = item.charges()
            item("skull_sceptre", 900, "Concentrating deeply, you divine that the sceptre has $charges ${"charge".plural(charges)} left.")
        }
    }
}
