package sim.engine.client.update.npc

import sim.engine.client.update.CharacterTask
import sim.engine.client.update.iterator.TaskIterator
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs

/**
 * Resets non-persistent changes
 */
class NPCResetTask(
    iterator: TaskIterator<NPC>,
    override val characters: NPCs = NPCs,
) : CharacterTask<NPC>(iterator) {

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun run(npc: NPC) {
        npc.visuals.reset()
        npc.steps.follow = npc.steps.previous
    }
}
