package sim.engine.client.update.iterator

import sim.engine.client.update.CharacterTask
import sim.engine.entity.character.Character

class SequentialIterator<C : Character> : TaskIterator<C> {
    override fun run(task: CharacterTask<C>) {
        for (character in task.characters) {
            if (task.predicate(character)) {
                task.run(character)
            }
        }
    }
}
