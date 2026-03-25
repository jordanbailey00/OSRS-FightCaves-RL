package sim.network.client.instruction

import sim.network.client.Instruction

/**
 * A freeform [message] a player wants (but has yet) to send directly to a [friend].
 */
data class ChatPrivate(
    val friend: String,
    val message: String,
) : Instruction
