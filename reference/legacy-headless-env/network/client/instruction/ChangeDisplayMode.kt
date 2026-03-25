package sim.network.client.instruction

import sim.network.client.Instruction

data class ChangeDisplayMode(
    val displayMode: Int,
    val width: Int,
    val height: Int,
    val antialiasLevel: Int,
) : Instruction
