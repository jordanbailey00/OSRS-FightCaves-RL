package content.achievement

import content.entity.player.dialogue.type.skillLamp
import content.entity.player.dialogue.type.statement
import sim.engine.Script
import sim.engine.entity.character.player.skill.exp.exp
import sim.engine.inv.inventory
import sim.engine.inv.remove

class AntiqueLamp : Script {

    init {
        itemOption("Rub", "antique_lamp_easy_lumbridge_tasks") { (item, slot) ->
            val skill = skillLamp()
            if (inventory.remove(slot, item.id)) {
                exp(skill, 500.0)
                statement("<blue>Your wish has been granted!<br><black>You have been awarded 500 ${skill.name} experience!")
            }
        }
    }
}
