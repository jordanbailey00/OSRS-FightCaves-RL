package sim.network.client.instruction

import sim.network.client.Instruction

data class Walk(val x: Int, val y: Int, val minimap: Boolean = false) : Instruction
