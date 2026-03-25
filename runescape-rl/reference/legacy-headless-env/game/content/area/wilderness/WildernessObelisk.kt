package content.area.wilderness

import content.skill.magic.book.modern.teleBlocked
import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.Areas
import sim.engine.entity.World
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.Teleport
import sim.engine.entity.obj.GameObject
import sim.engine.entity.obj.GameObjects
import sim.types.Tile
import sim.types.area.Rectangle
import sim.types.random

class WildernessObelisk : Script {

    val obelisks = Areas.tagged("obelisk")

    init {
        objectOperate("Activate", "wilderness_obelisk_*") { (target) ->
            if (World.containsQueue(target.id)) {
                return@objectOperate
            }
            val definition = Areas.getOrNull(target.id) ?: return@objectOperate
            val rectangle = (definition.area as Rectangle)
            replace(target, Tile(rectangle.minX - 1, rectangle.minY - 1))
            replace(target, Tile(rectangle.maxX + 1, rectangle.minY - 1))
            replace(target, Tile(rectangle.minX - 1, rectangle.maxY + 1))
            replace(target, Tile(rectangle.maxX + 1, rectangle.maxY + 1))
            World.queue(target.id, 7) {
                val obelisk = obelisks.random(random)
                for (player in Players.at(target.tile.zone)) {
                    if (player.tile !in rectangle || player.teleBlocked) {
                        continue
                    }
                    player.message("Ancient magic teleports you somewhere in the Wilderness!")
                    Teleport.teleport(player, obelisk.area.random(), "wilderness")
                }
            }
        }
    }

    fun replace(obj: GameObject, tile: Tile) {
        val sw = GameObjects.findOrNull(tile, obj.id) ?: return
        GameObjects.replace(sw, "wilderness_obelisk_glow", ticks = 8)
    }
}
