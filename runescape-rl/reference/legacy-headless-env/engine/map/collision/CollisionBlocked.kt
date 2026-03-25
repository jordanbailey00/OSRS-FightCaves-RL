package sim.engine.map.collision

import org.rsmod.game.pathfinder.StepValidator
import sim.engine.entity.character.Character
import sim.engine.get
import sim.types.Direction
import sim.types.Tile

fun Character.blocked(direction: Direction) = blocked(tile, direction)

fun Character.blocked(tile: Tile, direction: Direction): Boolean = !get<StepValidator>().canTravel(
    x = tile.x,
    z = tile.y,
    level = tile.level,
    size = size,
    offsetX = direction.delta.x,
    offsetZ = direction.delta.y,
    extraFlag = blockMove,
    collision = collision,
)
