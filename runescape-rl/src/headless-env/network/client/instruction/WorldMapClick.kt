package sim.network.client.instruction

import sim.network.client.Instruction

data class WorldMapClick(val tile: Int) : Instruction
