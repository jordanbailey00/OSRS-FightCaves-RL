package sim.engine.client.update.player

import sim.engine.client.update.CharacterTask
import sim.engine.client.update.batch.ZoneBatchUpdates
import sim.engine.client.update.iterator.TaskIterator
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.types.random

/**
 * Resets non-persistent changes
 */
class PlayerResetTask(
    iterator: TaskIterator<Player>,
    override val characters: Players = Players,
) : CharacterTask<Player>(iterator) {

    override fun run() {
        super.run()
        ZoneBatchUpdates.clear()
        if (!DEBUG && pidCounter++ > 100 && random.nextInt(50) == 0) {
            pidCounter = 0
            characters.shuffle()
        }
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun run(player: Player) {
        player.visuals.reset()
        player.steps.follow = player.steps.previous
    }

    companion object {
        private var pidCounter = 0
    }
}
