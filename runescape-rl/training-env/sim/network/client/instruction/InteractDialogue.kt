package sim.network.client.instruction

import sim.network.client.Instruction

data class InteractDialogue(
    val interfaceId: Int,
    val componentId: Int,
    val option: Int,
) : Instruction
