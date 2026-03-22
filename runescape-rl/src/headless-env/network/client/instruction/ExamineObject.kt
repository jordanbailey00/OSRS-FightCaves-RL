package sim.network.client.instruction

import sim.network.client.Instruction

@JvmInline
value class ExamineObject(
    val objectId: Int,
) : Instruction
