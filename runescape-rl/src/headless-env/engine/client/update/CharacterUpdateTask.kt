package sim.engine.client.update

import sim.engine.client.update.batch.ZoneBatchUpdates
import sim.engine.client.update.iterator.TaskIterator
import sim.engine.client.update.npc.NPCUpdateTask
import sim.engine.client.update.player.PlayerUpdateTask
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players

class CharacterUpdateTask(
    iterator: TaskIterator<Player>,
    private val playerUpdating: PlayerUpdateTask,
    private val npcUpdating: NPCUpdateTask,
) : CharacterTask<Player>(iterator) {
    override val characters: Iterable<Player> = Players

    override fun predicate(character: Player): Boolean = character.networked

    override fun run(character: Player) {
        ZoneBatchUpdates.run(character)
        playerUpdating.run(character)
        npcUpdating.run(character)
        character.viewport!!.shift()
        character.viewport!!.players.update()
    }
}
