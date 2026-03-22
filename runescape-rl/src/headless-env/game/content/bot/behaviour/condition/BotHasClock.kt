package content.bot.behaviour.condition

import sim.engine.client.variable.hasClock
import sim.engine.entity.character.player.Player

data class BotHasClock(val id: String) : Condition(1) {
    override fun keys() = setOf("clock:$id")
    override fun events() = setOf("clock")
    override fun check(player: Player) = player.hasClock(id)
}
