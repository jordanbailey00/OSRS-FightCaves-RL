package sim.network.client.instruction

import sim.network.client.Instruction

data class InteractObject(
    val objectId: Int,
    val x: Int,
    val y: Int,
    val option: Int,
) : Instruction
