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

class Darts : Script {

    init {
        itemOnItem("feather", "*_dart_tip") { _, toItem ->
            val xp = EnumDefinitions.intOrNull("dart_fletching_xp", toItem.id) ?: return@itemOnItem
            val level = EnumDefinitions.int("dart_fletching_level", toItem.id)

            if (!has(Skill.Fletching, level, true)) {
                return@itemOnItem
            }

            val currentFeathers = inventory.count("feather")
            val currentDartTips = inventory.count(toItem.id)

            val actualAmount = minOf(currentFeathers, currentDartTips, 10)

            if (actualAmount < 1) {
                message("You don't have enough materials to fletch bolts.", ChatType.Game)
                return@itemOnItem
            }

            val createdDart: String = toItem.id.replace("_tip", "")
            val success = inventory.transaction {
                remove(toItem.id, actualAmount)
                remove("feather", actualAmount)
                add(createdDart, actualAmount)
            }

            if (!success) {
                return@itemOnItem
            }

            val totalExperience = (xp / 10.0) * actualAmount
            experience.add(Skill.Fletching, totalExperience)
            message("You finish making $actualAmount darts.", ChatType.Game)
        }
    }
}
