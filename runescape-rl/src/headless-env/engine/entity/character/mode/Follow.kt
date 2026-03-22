package sim.engine.entity.character.mode

import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.move.Movement
import sim.engine.entity.character.mode.move.target.FollowTargetStrategy
import sim.engine.entity.character.mode.move.target.TargetStrategy
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.distanceTo
import sim.types.Tile

class Follow(
    character: Character,
    val target: Character,
    private val strategy: TargetStrategy = FollowTargetStrategy(target),
) : Movement(character, strategy) {

    private var smart = character is Player

    override fun tick() {
        if (!character.watching(target)) {
            character.watch(target)
        }
        if (target.tile.level != character.tile.level) {
            if (character is NPC) {
                character.tele(strategy.tile, clearMode = false)
            } else {
                character.mode = EmptyMode
            }
            return
        }
        if (character is NPC && character.tile.distanceTo(target) > 15) {
            character.tele(strategy.tile, clearMode = false)
            character.clearWatch()
        }
        if (!smart) {
            character.steps.clearDestination()
        }
        super.tick()
    }

    override fun recalculate(): Boolean {
        if (character.steps.isEmpty()) {
            smart = false
        }
        if (!equals(strategy.tile, character.steps.destination)) {
            character.steps.queueStep(strategy.tile)
            return true
        }
        return false
    }

    override fun getTarget(): Tile? {
        val target = character.steps.peek()
        if (!smart && target == null) {
            recalculate()
            return character.steps.peek()
        }
        return super.getTarget()
    }

    override fun onCompletion() {
    }

    override fun stop(replacement: Mode) {
        if (replacement !is Follow) {
            character.clearWatch()
        }
    }
}
