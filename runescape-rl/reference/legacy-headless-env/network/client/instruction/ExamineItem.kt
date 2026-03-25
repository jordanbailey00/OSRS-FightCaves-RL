package sim.network.client.instruction

import sim.network.client.Instruction

@JvmInline
value class ExamineItem(
    val itemId: Int,
) : Instruction
