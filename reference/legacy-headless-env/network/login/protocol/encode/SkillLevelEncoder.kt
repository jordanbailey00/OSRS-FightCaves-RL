package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.login.Protocol.SKILL_LEVEL
import sim.network.login.protocol.writeByteInverse
import sim.network.login.protocol.writeByteSubtract
import sim.network.login.protocol.writeIntMiddle

/**
 * Updates the players' skill level & experience
 * @param skill The skills id
 * @param level The current players level
 * @param experience The current players experience
 */
fun Client.skillLevel(
    skill: Int,
    level: Int,
    experience: Int,
) = send(SKILL_LEVEL) {
    writeByteSubtract(level)
    writeByteInverse(skill)
    writeIntMiddle(experience)
}
