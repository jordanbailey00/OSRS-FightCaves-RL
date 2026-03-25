package content.quest.member.enakhras_lament

import content.entity.player.dialogue.type.statement
import content.skill.magic.jewellery.jewelleryTeleport
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.chat.plural
import sim.engine.data.definition.Areas
import sim.engine.inv.charges
import sim.engine.inv.inventory
import sim.engine.inv.replace

class Camulet : Script {

    init {
        itemOption("Rub", "camulet") {
            if (jewelleryTeleport(this, it.inventory, it.slot, Areas["camulet_teleport"])) {
                message("You rub the amulet...")
            } else {
                statement("Your Camulet has run out of teleport charges. You can renew them by applying camel dung.")
            }
        }

        itemOption("Check-charge", "camulet") {
            if (it.inventory != "inventory") {
                return@itemOption
            }
            val charges = inventory.charges(this, it.slot)
            message("Your Camulet has $charges ${"charge".plural(charges)} left.")
            if (charges == 0) {
                message("You can recharge it by applying camel dung.")
            }
        }

        itemOnItem("ugthanki_dung", "camulet") { fromItem, _, fromSlot, toSlot ->
            val slot = if (fromItem.id == "camulet") fromSlot else toSlot
            val charges = inventory.charges(this, slot)
            if (charges == 4) {
                message("Your Camulet already has 4 charges.")
                return@itemOnItem
            }
            if (inventory.replace("ugthanki_dung", "bucket")) {
                message("You recharge the Camulet using camel dung. Yuck!")
                set("camulet_charges", 4)
            }
        }
    }
}
