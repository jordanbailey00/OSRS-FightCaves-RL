package content.skill.fletching

import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.EnumDefinitions
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level.has
import sim.engine.inv.inventory
import sim.engine.inv.transact.operation.AddItem.add
import sim.engine.inv.transact.operation.RemoveItem.remove

class Bolts : Script {

    init {
        itemOnItem("feather", "*_bolts_unf") { _, toItem ->
            val xp = EnumDefinitions.intOrNull("bolt_fletching_xp", toItem.id) ?: return@itemOnItem
            val level = EnumDefinitions.int("bolt_fletching_level", toItem.id)

            if (!has(Skill.Fletching, level, true)) {
                return@itemOnItem
            }

            val currentFeathers = inventory.count("feather")
            val currentBoltUnf = inventory.count(toItem.id)

            val actualAmount = minOf(currentFeathers, currentBoltUnf, 10)

            if (actualAmount < 1) {
                message("You don't have enough materials to fletch bolts.", ChatType.Game)
                return@itemOnItem
            }

            val createdBolt: String = toItem.id.replace("_unf", "")
            val success = inventory.transaction {
                remove(toItem.id, actualAmount)
                remove("feather", actualAmount)
                add(createdBolt, actualAmount)
            }

            if (!success) {
                return@itemOnItem
            }

            val totalExperience = (xp / 10.0) * actualAmount
            experience.add(Skill.Fletching, totalExperience)
            message("You fletch $actualAmount bolts.", ChatType.Game)
        }
    }
}
