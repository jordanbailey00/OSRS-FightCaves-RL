package content.bot.behaviour.condition

import sim.engine.GameLoop
import sim.engine.client.variable.remaining
import sim.engine.entity.character.player.Player
import sim.engine.timer.epochSeconds

data class BotClockRemaining(val id: String, val min: Int? = null, val max: Int? = null, val seconds: Boolean = false) : Condition(1) {
    override fun keys() = setOf("clock:$id")
    override fun events() = setOf("clock")
    override fun check(player: Player): Boolean {
        val remaining = player.remaining(id, if (seconds) epochSeconds() else GameLoop.tick)
        return inRange(remaining, min, max)
    }
}
