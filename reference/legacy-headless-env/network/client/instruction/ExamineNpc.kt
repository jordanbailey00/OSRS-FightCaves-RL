package sim.network.client.instruction

import sim.network.client.Instruction

@JvmInline
value class ExamineNpc(
    val npcId: Int,
) : Instruction
