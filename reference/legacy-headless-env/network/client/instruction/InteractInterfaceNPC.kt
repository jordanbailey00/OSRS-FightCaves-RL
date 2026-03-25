package sim.network.client.instruction

import sim.network.client.Instruction

data class InteractInterfaceNPC(
    val npcIndex: Int,
    val interfaceId: Int,
    val componentId: Int,
    val itemId: Int,
    val itemSlot: Int,
) : Instruction
