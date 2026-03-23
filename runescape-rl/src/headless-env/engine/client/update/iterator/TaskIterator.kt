package sim.engine.client.update.iterator

import sim.engine.client.update.CharacterTask
import sim.engine.entity.character.Character

interface TaskIterator<C : Character> {

    fun run(task: CharacterTask<C>)
}
