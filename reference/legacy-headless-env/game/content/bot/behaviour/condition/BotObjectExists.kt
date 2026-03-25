package content.bot.behaviour.condition

import sim.engine.entity.character.player.Player
import sim.engine.entity.obj.GameObjects

data class BotObjectExists(val id: String, val x: Int, val y: Int) : Condition(900) {
    override fun keys() = setOf("obj:$id")
    override fun events() = setOf("object")
    override fun check(player: Player) = GameObjects.findOrNull(player.tile.copy(x, y), id) != null
}
