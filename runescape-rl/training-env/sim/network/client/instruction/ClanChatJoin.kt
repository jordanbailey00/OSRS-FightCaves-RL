package sim.network.client.instruction

import sim.network.client.Instruction

/**
 * Player wants to join a clan chat
 * @param name The display name of the friend whose chat to join
 */
data class ClanChatJoin(
    val name: String,
) : Instruction
