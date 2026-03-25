package sim.engine.entity

import sim.engine.entity.character.Character
import sim.engine.entity.obj.GameObject
import sim.types.Tile

/**
 * An identifiable object with a physical spatial location
 */
interface Entity {
    var tile: Tile
}

fun Tile.distanceTo(entity: Entity) = when (entity) {
    is Character -> distanceTo(entity.tile, entity.size, entity.size)
    is GameObject -> distanceTo(entity.tile, entity.width, entity.height)
    else -> distanceTo(entity.tile)
}
