package sim.engine.client.instruction.handle

import com.github.michaelbull.logging.InlineLogger
import sim.engine.Script
import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.instruction.InterfaceHandler
import sim.engine.client.ui.InterfaceOption
import sim.engine.data.definition.InterfaceDefinitions
import sim.engine.client.ui.InterfaceApi
import sim.engine.entity.character.player.Player
import sim.network.client.instruction.InteractInterface

class InterfaceOptionHandler(
    private val handler: InterfaceHandler,
) : InstructionHandler<InteractInterface>() {

    private val logger = InlineLogger()

    override fun validate(player: Player, instruction: InteractInterface): Boolean {
        val (interfaceId, componentId, itemId, itemSlot, option) = instruction

        var (id, component, item, options) = handler.getInterfaceItem(player, interfaceId, componentId, itemId, itemSlot) ?: return false

        if (options == null) {
            options = InterfaceDefinitions.getComponent(id, component)?.getOrNull("options") ?: emptyArray()
        }

        if (option !in options.indices) {
            logger.info { "Interface option not found [$player, interface=$interfaceId, component=$componentId, option=$option, options=${options.toList()}]" }
            return false
        }

        val selectedOption = options.getOrNull(option) ?: ""
        val event = InterfaceOption(
            interfaceComponent = "$id:$component",
            optionIndex = option,
            option = selectedOption,
            item = item,
            itemSlot = itemSlot,
        )
        Script.launch {
            InterfaceApi.option(player, event)
        }
        return true
    }
}
