package content.skill.crafting

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.skill.Skill

class SoftClay : Script {

    init {
        crafted(Skill.Crafting) { def ->
            if (def.add.any { it.id == "soft_clay" }) {
                message("You now have some soft, workable clay.", ChatType.Filter)
            }
        }
    }
}
