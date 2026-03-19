package sim.network.client.instruction

import sim.network.client.Instruction

data class InteractInterface(
    val interfaceId: Int,
    val componentId: Int,
    val itemId: Int,
    val itemSlot: Int,
    val option: Int,
) : Instruction
