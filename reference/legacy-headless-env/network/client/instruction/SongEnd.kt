package sim.network.client.instruction

import sim.network.client.Instruction

data class SongEnd(
    val songIndex: Int,
) : Instruction
