package content.skill.herblore

import sim.engine.Script
import sim.engine.data.definition.EnumDefinitions
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level.has
import sim.engine.inv.inventory
import sim.engine.inv.replace

class HerbCleaning : Script {

    init {
        itemOption("Clean") { (item, slot) ->
            val level = EnumDefinitions.intOrNull("herb_cleaning_level", item.id) ?: return@itemOption
            if (!has(Skill.Herblore, level, true)) {
                return@itemOption
            }

            if (inventory.replace(slot, item.id, item.id.replace("grimy", "clean"))) {
                val xp = EnumDefinitions.int("herb_cleaning_xp", item.id) / 10.0
                experience.add(Skill.Herblore, xp)
            }
        }
    }
}
