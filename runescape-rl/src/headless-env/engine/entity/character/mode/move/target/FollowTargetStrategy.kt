package sim.engine.entity.character.mode.move.target

import sim.engine.entity.character.Character
import sim.types.Tile

data class FollowTargetStrategy(
    private val character: Character,
) : TargetStrategy {
    override val bitMask = 0
    override val tile: Tile
        get() = character.steps.follow
    override val width: Int
        get() = character.size
    override val height: Int
        get() = character.size
    override val rotation = 0
    override val shape = -1
    override val sizeX: Int
        get() = character.size
    override val sizeY: Int
        get() = character.size
}
