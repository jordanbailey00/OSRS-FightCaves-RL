package sim.network.client.instruction

import sim.network.client.Instruction

data class InteractNPC(
    val npcIndex: Int,
    val option: Int,
) : Instruction
