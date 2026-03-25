package sim.engine.entity.character.mode.move.target

import sim.types.Tile

object DefaultTargetStrategy : TargetStrategy {
    override val bitMask = 0
    override val tile = Tile.EMPTY
    override val width: Int = 1
    override val height: Int = 1
    override val rotation = 0
    override val shape = -1
    override val sizeX: Int = 1
    override val sizeY: Int = 1
}
