package content.bot.behaviour.condition

import sim.engine.entity.character.player.Player
import sim.engine.inv.inventory

data class BotInventorySetup(val items: List<BotItem>) : Condition(100) {
    override fun keys() = items.flatMap { entry -> entry.ids.map { "item:$it" } }.toSet()
    override fun events() = setOf("inv:inventory")
    override fun check(player: Player) = contains(player, player.inventory, items)
}
