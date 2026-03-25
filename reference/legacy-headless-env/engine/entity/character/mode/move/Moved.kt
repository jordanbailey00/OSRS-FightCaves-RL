package sim.engine.entity.character.mode.move

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import sim.engine.data.definition.AreaDefinition
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.event.Wildcard
import sim.engine.event.Wildcards
import sim.types.Area
import sim.types.Tile

interface Moved {
    /**
     * Entity moved starting at from and ending at their current tile
     */
    fun moved(handler: Player.(from: Tile) -> Unit) {
        playerMoved.add(handler)
    }

    fun npcMoved(id: String = "*", handler: NPC.(from: Tile) -> Unit) {
        Wildcards.find(id, Wildcard.Npc) { match ->
            npcMoved.getOrPut(match) { mutableListOf() }.add(handler)
        }
    }

    fun entered(area: String, handler: Player.(area: AreaDefinition) -> Unit) {
        entered.getOrPut(area) { mutableListOf() }.add(handler)
    }

    fun exited(area: String, handler: Player.(area: AreaDefinition) -> Unit) {
        exited.getOrPut(area) { mutableListOf() }.add(handler)
    }

    companion object : AutoCloseable {
        private val entered = Object2ObjectOpenHashMap<String, MutableList<Player.(AreaDefinition) -> Unit>>(25)
        private val exited = Object2ObjectOpenHashMap<String, MutableList<Player.(AreaDefinition) -> Unit>>(25)
        val playerMoved = ObjectArrayList<(Player, Tile) -> Unit>(15)
        private val npcMoved = Object2ObjectOpenHashMap<String, MutableList<(NPC, Tile) -> Unit>>(10)

        fun enter(player: Player, id: String, area: AreaDefinition) {
            for (handler in entered[id] ?: emptyList()) {
                handler(player, area)
            }
            for (handler in entered["*"] ?: return) {
                handler(player, area)
            }
        }

        fun exit(player: Player, id: String, area: AreaDefinition) {
            for (handler in exited[id] ?: emptyList()) {
                handler(player, area)
            }
            for (handler in exited["*"] ?: return) {
                handler(player, area)
            }
        }

        fun player(player: Player, from: Tile) {
            for (handler in playerMoved) {
                handler(player, from)
            }
        }

        fun npc(npc: NPC, from: Tile) {
            for (handler in npcMoved[npc.id] ?: emptyList()) {
                handler(npc, from)
            }
            for (handler in npcMoved["*"] ?: return) {
                handler(npc, from)
            }
        }

        fun hasNpcMovedHandler(id: String): Boolean = npcMoved.containsKey(id) || npcMoved.containsKey("*")

        override fun close() {
            entered.clear()
            exited.clear()
            playerMoved.clear()
            npcMoved.clear()
        }

    }
}
