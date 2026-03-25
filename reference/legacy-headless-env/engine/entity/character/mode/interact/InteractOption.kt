package sim.engine.entity.character.mode.interact

import sim.engine.entity.Entity
import sim.engine.entity.character.Character

/**
 * Just a helper for checking basic interactions
 */
abstract class InteractOption(
    character: Character,
    target: Entity,
    approachRange: Int? = null,
    shape: Int? = null,
) : Interact(character, target, approachRange = approachRange, shape = shape) {
    abstract val option: String
}