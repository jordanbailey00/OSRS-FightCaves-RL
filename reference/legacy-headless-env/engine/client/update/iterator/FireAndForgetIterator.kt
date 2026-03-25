package sim.engine.client.update.iterator

import com.github.michaelbull.logging.InlineLogger
import sim.engine.client.update.CharacterTask
import sim.engine.entity.character.Character
import java.util.concurrent.Executors

class FireAndForgetIterator<C : Character> : TaskIterator<C> {
    private val logger = InlineLogger()
    private val executor = Executors.newFixedThreadPool(2000)

    override fun run(task: CharacterTask<C>) {
        for (character in task.characters) {
            if (task.predicate(character)) {
                executor.execute {
                    try {
                        task.run(character)
                    } catch (t: Throwable) {
                        logger.warn(t) { "Exception in parallel faf task." }
                    }
                }
            }
        }
    }
}
