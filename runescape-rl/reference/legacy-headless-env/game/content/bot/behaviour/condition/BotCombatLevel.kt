package content.bot.behaviour.condition

import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.combatLevel

data class BotCombatLevel(val min: Int? = null, val max: Int? = null) : Condition(1) {
    override fun keys() = setOf("skill:combat")
    override fun events() = setOf("skill:attack", "skill:strength", "skill:defence", "skill:ranged", "skill:magic", "skill:summoning", "skill:constitution")
    override fun check(player: Player) = inRange(player.combatLevel, min, max)
}
