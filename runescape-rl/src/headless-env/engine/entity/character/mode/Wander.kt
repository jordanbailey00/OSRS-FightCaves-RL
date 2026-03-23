package sim.engine.entity.character.mode

import sim.engine.data.Settings
import sim.engine.entity.character.mode.move.Movement
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.npc.NPC
import sim.types.Tile
import sim.types.random

/**
 * Random walking
 * NPCs that don't move for more than [stuckLimit] ticks are teleported back to [spawn]
 */
class Wander(
    private val npc: NPC,
    private val spawn: Tile = npc["spawn_tile"]!!,
    private val stuckLimit: Int = npc.def["stuck_limit", 500],
) : Movement(npc) {

    private var stuckCounter = 0
    private var lastTile = Tile.EMPTY

    override fun tick() {
        if (npc.tile == lastTile) {
            stuckCounter = 0
        } else if (stuckCounter++ >= stuckLimit) {
            npc.tele(spawn)
            stuckCounter = 0
        }
        lastTile = npc.tile
        if (random.nextInt(8) != 0) {
            super.tick()
            return
        }
        val radius: Int = npc.def["wander_range", 5]
        if (radius <= 0) {
            npc.mode = EmptyMode
            return
        }
        val tile = spawn.toCuboid(radius).random()
        character.steps.queueStep(tile)
        super.tick()
    }

    override fun onCompletion() {
    }

    companion object {
        fun wanders(npc: NPC): Boolean {
            if (!Settings["world.npcs.randomWalk", false]) {
                return false
            }
            return when (npc.def.walkMode.toInt()) {
                ModeType.WANDER_THROUGH, ModeType.WANDER_SPECIAL, ModeType.WANDER_WATER -> true
                else -> false
            }
        }
    }
}
