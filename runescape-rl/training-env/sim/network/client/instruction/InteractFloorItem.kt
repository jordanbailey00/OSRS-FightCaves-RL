package sim.network.client.instruction

import sim.network.client.Instruction

data class InteractFloorItem(
    val id: Int,
    val x: Int,
    val y: Int,
    val option: Int,
) : Instruction
