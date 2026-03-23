package sim.engine.client.update

import sim.engine.client.update.iterator.TaskIterator
import sim.engine.client.variable.hasClock
import sim.engine.entity.character.mode.EmptyMode
import sim.engine.entity.character.mode.Wander
import sim.engine.entity.character.mode.Wander.Companion.wanders
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.skill.Skill

class NPCTask(
    iterator: TaskIterator<NPC>,
    override val characters: Iterable<NPC> = NPCs,
) : CharacterTask<NPC>(iterator) {

    override fun run(character: NPC) {
        checkDelay(character)
        if (character.mode == EmptyMode && wanders(character)) {
            character.mode = Wander(character)
        }
        healthRegen(character)
        character.softTimers.run()
        character.queue.tick()
        character.mode.tick()
        checkTileFacing(character)
    }

    private fun healthRegen(character: NPC) {
        if (!character.hasClock("under_attack") && character.regenCounter++ >= character.def["regen_rate_ticks", 25] && character.levels.get(Skill.Constitution) < character.levels.getMax(Skill.Constitution)) {
            character.levels.restore(Skill.Constitution, 10)
            character.regenCounter = 0
        }
    }
}
