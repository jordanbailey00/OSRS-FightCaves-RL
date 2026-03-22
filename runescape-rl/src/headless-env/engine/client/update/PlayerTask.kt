package sim.engine.client.update

import sim.engine.client.ui.hasMenuOpen
import sim.engine.client.update.iterator.TaskIterator
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players

class PlayerTask(
    iterator: TaskIterator<Player>,
    override val characters: Iterable<Player> = Players,
) : CharacterTask<Player>(iterator) {

    override fun run(character: Player) {
        checkDelay(character)
        character.queue.tick()
        if (!character.contains("delay") && !character.hasMenuOpen()) {
            character.timers.run()
        }
        character.softTimers.run()
//        character.area.tick()
        character.mode.tick()
        checkTileFacing(character)
    }
}
