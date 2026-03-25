package sim.network.client.instruction

import sim.network.client.Instruction

data class IgnoreDelete(
    val name: String,
) : Instruction
