package content.quest.member.mahjarrat.the_dig_site

import content.skill.magic.jewellery.jewelleryTeleport
import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.Areas
import sim.engine.entity.character.player.chat.ChatType

class DigSitePendant : Script {

    init {
        itemOption("Rub", "dig_site_pendant_#") {
            if (contains("delay")) {
                return@itemOption
            }
            message("You rub the pendant...", ChatType.Filter)
            jewelleryTeleport(this, it.inventory, it.slot, Areas["dig_site_teleport"])
        }
    }
}
