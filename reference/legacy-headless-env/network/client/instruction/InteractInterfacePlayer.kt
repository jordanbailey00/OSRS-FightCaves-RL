package sim.network.client.instruction

import sim.network.client.Instruction

data class InteractInterfacePlayer(
    val playerIndex: Int,
    val interfaceId: Int,
    val componentId: Int,
    val itemId: Int,
    val itemSlot: Int,
) : Instruction
