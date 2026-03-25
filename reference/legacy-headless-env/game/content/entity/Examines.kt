package content.entity

import sim.engine.Script
import sim.engine.client.instruction.instruction
import sim.engine.client.message
import sim.engine.client.ui.InterfaceOption
import sim.engine.data.definition.ItemDefinitions
import sim.engine.data.definition.NPCDefinitions
import sim.engine.data.definition.ObjectDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.ChatType
import sim.network.client.instruction.ExamineItem
import sim.network.client.instruction.ExamineNpc
import sim.network.client.instruction.ExamineObject

class Examines : Script {

    init {
        interfaceOption("Examine", "inventory:inventory", ::examineItem)
        interfaceOption("Examine", "worn_equipment:item", ::examineItem)
        interfaceOption("Examine", "bank:inventory", ::examineItem)
        interfaceOption("Examine", "price_checker:items", ::examineItem)
        interfaceOption("Examine", "equipment_bonuses:inventory", ::examineItem)
        interfaceOption("Examine", "trade_main:offer_options", ::examineItem)
        interfaceOption("Examine", "trade_main:offer_warning", ::examineItem)
        interfaceOption("Examine<col=FF9040>", "trade_main:other_options", ::examineItem)
        interfaceOption("Examine", "trade_main:other_warning", ::examineItem)
        interfaceOption("Examine", "trade_main:loan_item", ::examineItem)
        interfaceOption("Examine", "trade_main:other_loan_item", ::examineItem)
        interfaceOption("Examine", "farming_equipment_store_side:*", ::examineItem)
        interfaceOption("Examine", "farming_equipment_store:*", ::examineItem)

        itemOption("Examine", inventory = "*") { (item) ->
            message(item.def.getOrNull("examine") ?: return@itemOption, ChatType.ItemExamine)
        }

        objectApproach("Examine") { (target) ->
            message(target.def.getOrNull("examine") ?: return@objectApproach, ChatType.ObjectExamine)
        }

        npcApproach("Examine") { (target) ->
            message(target.def(this).getOrNull("examine") ?: return@npcApproach, ChatType.NPCExamine)
        }

        instruction<ExamineItem> { player ->
            val definition = ItemDefinitions.get(itemId)
            if (definition.contains("examine")) {
                player.message(definition["examine"], ChatType.Game)
            }
        }

        instruction<ExamineNpc> { player ->
            val definition = NPCDefinitions.get(npcId)
            if (definition.contains("examine")) {
                player.message(definition["examine"], ChatType.Game)
            }
        }

        instruction<ExamineObject> { player ->
            val definition = ObjectDefinitions.get(objectId)
            if (definition.contains("examine")) {
                player.message(definition["examine"], ChatType.Game)
            }
        }
    }

    private fun examineItem(player: Player, option: InterfaceOption) {
        player.message(option.item.def.getOrNull("examine") ?: return, ChatType.ItemExamine)
    }
}
