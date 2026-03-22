package sim.engine.entity.character

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.event.*
import sim.types.Tile

interface Death {

    data class OnDeath(
        var dropItems: Boolean = true,
        var teleport: Tile? = null,
    )

    fun playerDeath(handler: Player.(OnDeath) -> Unit) {
        playerHandlers.add(handler)
    }

    fun npcDeath(npc: String = "*", handler: NPC.() -> Unit) {
        Wildcards.find(npc, Wildcard.Npc) { id ->
            npcHandlers.getOrPut(id) { mutableListOf() }.add(handler)
        }
    }

    companion object : AutoCloseable {
        private val playerHandlers = ObjectArrayList<Player.(OnDeath) -> Unit>(20)
        private val npcHandlers = Object2ObjectOpenHashMap<String, MutableList<NPC.() -> Unit>>(20)

        fun killed(player: Player): OnDeath {
            val onDeath = OnDeath()
            for (handler in playerHandlers) {
                handler.invoke(player, onDeath)
            }
            return onDeath
        }

        fun killed(npc: NPC) {
            for (handler in npcHandlers[npc.id] ?: emptyList()) {
                handler.invoke(npc)
            }
            for (handler in npcHandlers["*"] ?: emptyList()) {
                handler.invoke(npc)
            }
        }

        fun hasNpcDeathHandler(id: String): Boolean = npcHandlers.containsKey(id) || npcHandlers.containsKey("*")

        override fun close() {
            playerHandlers.clear()
            npcHandlers.clear()
        }
    }
}

