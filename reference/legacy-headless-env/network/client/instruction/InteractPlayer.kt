package sim.network.client.instruction

import sim.network.client.Instruction

data class InteractPlayer(
    val playerIndex: Int,
    val option: Int,
) : Instruction
