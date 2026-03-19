package sim.network.client.instruction

import sim.network.client.Instruction

data class FriendDelete(
    val friendsName: String,
) : Instruction
