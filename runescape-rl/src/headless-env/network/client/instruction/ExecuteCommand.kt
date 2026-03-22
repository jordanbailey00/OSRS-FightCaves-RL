package sim.network.client.instruction

import sim.network.client.Instruction

data class ExecuteCommand(val command: String, val automatic: Boolean, val tab: Boolean) : Instruction
