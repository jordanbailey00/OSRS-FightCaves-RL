package content.entity

import content.area.misthalin.Border
import sim.engine.Script
import sim.engine.client.instruction.instruction
import sim.engine.client.ui.closeInterfaces
import sim.engine.data.Settings
import sim.engine.data.definition.Areas
import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.EmptyMode
import sim.engine.entity.character.mode.PauseMode
import sim.engine.entity.character.mode.interact.PlayerOnObjectInteract
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Players
import sim.engine.map.collision.Collisions
import sim.network.client.instruction.Walk
import sim.types.Distance.nearestTo
import sim.types.Zone
import sim.types.area.Rectangle

class Movement : Script {

    val borders = mutableMapOf<Zone, Rectangle>()

    init {
        playerSpawn {
            if (Players.add(this) && Settings["world.players.collision", false]) {
                add(this)
            }
        }

        npcSpawn {
            if (Settings["world.npcs.collision", false]) {
                add(this)
            }
        }

        npcMoved(handler = NPCs::update)
        moved(handler = Players::update)

        worldSpawn {
            for (border in Areas.tagged("border")) {
                val passage = border.area as Rectangle
                for (zone in passage.toZones()) {
                    borders[zone] = passage
                }
            }
        }

        instruction<Walk> { player ->
            if (player.contains("delay")) {
                return@instruction
            }
            if (player.mode is PlayerOnObjectInteract) {
                player.clearAnim()
            }
            player.closeInterfaces()
            player.clearWatch()
            player.queue.clearWeak()
            player.suspension = null
            if (minimap && !player["a_world_in_microcosm_task", false]) {
                player["a_world_in_microcosm_task"] = true
            }

            val target = player.tile.copy(x, y)
            val border = borders[target.zone]
            if (border != null && (target in border || player.tile in border)) {
                val tile = border.nearestTo(player.tile)
                val endSide = Border.getOppositeSide(border, tile)
                player.walkTo(endSide, noCollision = true, forceWalk = true)
            } else {
                if (player.tile == target && player.mode != EmptyMode && player.mode != PauseMode) {
                    player.mode = EmptyMode
                }
                player.walkTo(target)
            }
        }

        playerDespawn {
            if (Settings["world.players.collision", false]) {
                remove(this)
            }
        }

        npcDeath {
            remove(this)
        }

        npcDespawn {
            if (Settings["world.npcs.collision", false]) {
                remove(this)
            }
        }
    }

    fun add(char: Character) {
        val mask = char.collisionFlag
        val size = char.size
        for (x in char.tile.x until char.tile.x + size) {
            for (y in char.tile.y until char.tile.y + size) {
                Collisions.add(x, y, char.tile.level, mask)
            }
        }
    }

    fun remove(char: Character) {
        val mask = char.collisionFlag
        val size = char.size
        for (x in 0 until size) {
            for (y in 0 until size) {
                Collisions.remove(char.tile.x + x, char.tile.y + y, char.tile.level, mask)
            }
        }
    }
}
