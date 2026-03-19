package sim.network.client.instruction

import sim.network.client.Instruction

data class ClanChatRank(
    val name: String,
    val rank: Int,
) : Instruction
