package sim.network.client.instruction

import sim.network.client.Instruction

data class FriendAdd(
    val friendsName: String,
) : Instruction
