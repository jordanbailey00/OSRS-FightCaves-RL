package content.quest.member.ghosts_ahoy

import sim.engine.Script
import sim.engine.client.instruction.handle.interactItemOn
import sim.engine.client.message
import sim.engine.entity.character.player.Teleport
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.item.Item
import sim.engine.entity.obj.GameObjects
import sim.engine.inv.inventory
import sim.engine.inv.replace
import sim.types.Tile

class Ectophial : Script {

    init {
        itemOption("Empty", "ectophial") {
            gfx("empty_ectophial")
            animDelay("empty_ectophial")
            delay(2)
            Teleport.teleport(this, "ectophial_teleport", "ectophial")
        }

        itemOnObjectOperate("ectophial_empty", "ectofuntus") {
            if (inventory.replace(it.slot, it.item.id, "ectophial")) {
                anim("take")
                message("You refill the ectophial from the Ectofuntus.")
            }
        }

        teleportTakeOff("ectophial") {
            anim("empty_ectophial")
            gfx("empty_ectophial")
            message("You empty the ectoplasm onto the ground around your feet...", ChatType.Filter)
            return@teleportTakeOff true
        }

        teleportLand("ectophial") {
            message("... and the world changes around you.", ChatType.Filter)
            val ectofuntus = GameObjects.findOrNull(Tile(3658, 3518), "ectofuntus") ?: return@teleportLand
            val slot = inventory.indexOf("ectophial")
            interactItemOn(ectofuntus, "inventory", "inventory", Item("empty_ectophial"), slot)
        }
    }
}
