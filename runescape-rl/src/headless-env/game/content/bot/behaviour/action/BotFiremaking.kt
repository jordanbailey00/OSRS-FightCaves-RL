package content.bot.behaviour.action

import content.bot.Bot
import content.bot.behaviour.BehaviourFrame
import content.bot.behaviour.BehaviourState
import content.bot.behaviour.BotWorld
import content.bot.behaviour.Reason
import sim.engine.data.definition.Areas
import sim.engine.entity.character.mode.EmptyMode
import sim.engine.entity.character.mode.move.canTravel
import sim.engine.entity.obj.GameObjects
import sim.engine.entity.obj.ObjectLayer
import sim.engine.get
import sim.engine.inv.inventory
import sim.network.client.instruction.Walk
import sim.types.Tile

data class BotFiremaking(val item: String, val area: String) : BotAction {
    override fun update(bot: Bot, world: BotWorld, frame: BehaviourFrame): BehaviourState {
        when {
            bot.mode != EmptyMode -> return BehaviourState.Running
            cantLightOn(bot.tile) -> {
                // One tile ahead
                if (world.canTravel(bot.player.tile, -1, 0)) {
                    world.execute(bot.player, Walk(bot.tile.x - 1, bot.tile.y))
                    return BehaviourState.Wait(1, BehaviourState.Running)
                }
                // Two tiles ahead
                if (world.canTravel(bot.tile.level, bot.tile.x - 1, bot.tile.y, -1, 0)) {
                    world.execute(bot.player, Walk(bot.tile.x - 2, bot.tile.y))
                    return BehaviourState.Wait(1, BehaviourState.Running)
                }
                val area = Areas[area]
                for (tile in area) {
                    if (cantLightOn(tile)) {
                        continue
                    }
                    world.execute(bot.player, Walk(tile.x, tile.y))
                    return BehaviourState.Wait(1, BehaviourState.Running)
                }
                return BehaviourState.Failed(Reason.Stuck)
            }
            bot.player.inventory.contains(item) -> return BotItemOnItem.itemOnItem(bot.player, world, "tinderbox", item) ?: BehaviourState.Running
            else -> return BehaviourState.Success
        }
    }

    private fun cantLightOn(tile: Tile): Boolean = GameObjects.getLayer(tile, ObjectLayer.GROUND) != null
}
