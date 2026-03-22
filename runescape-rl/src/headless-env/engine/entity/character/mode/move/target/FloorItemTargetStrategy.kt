package sim.engine.entity.character.mode.move.target

import sim.engine.client.variable.hasClock
import sim.engine.entity.character.Character
import sim.engine.entity.item.floor.FloorItem
import sim.types.Tile

data class FloorItemTargetStrategy(
    private val floorItem: FloorItem,
) : TargetStrategy {
    override val bitMask = 0
    override val tile: Tile = floorItem.tile
    override val width: Int = 1
    override val height: Int = 1
    override val rotation = 0
    override val shape = 10
    override val sizeX: Int = 1
    override val sizeY: Int = 1

    override fun reached(character: Character): Boolean {
        if (character.tile.within(tile, 1) && (character.steps.isEmpty() || character.hasClock("movement_delay"))) {
            return super.reached(character) || DefaultTargetStrategy.reached(character)
        }
        return DefaultTargetStrategy.reached(character)
    }
}
