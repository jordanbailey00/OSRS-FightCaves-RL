package content.entity.player.command

import sim.engine.Script
import sim.engine.client.command.adminCommand
import sim.engine.client.command.modCommand
import sim.engine.client.command.stringArg
import sim.engine.client.message
import sim.engine.client.variable.start
import sim.engine.data.definition.NPCDefinitions
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.types.Direction
import kotlin.text.toIntOrNull

class NPCCommands : Script {

    init {
        modCommand("npcs", desc = "Get total npc count") {
            message("NPCs: ${NPCs.count()}")
        }
        adminCommand("npc", stringArg("npc-id", autofill = NPCDefinitions.ids.keys), desc = "Spawn an npc", handler = ::spawn)
    }

    fun spawn(player: Player, args: List<String>) {
        val id = args[0].toIntOrNull()
        val definition = if (id != null) NPCDefinitions.getOrNull(id) else NPCDefinitions.getOrNull(args[0])
        if (definition == null) {
            player.message("Unable to find npc with id ${args[0]}.")
            return
        }
        println("{ id = \"${definition.stringId}\", x = ${player.tile.x}, y = ${player.tile.y}, level = ${player.tile.level}, members = true },")
        val npc = NPCs.add(definition.stringId, player.tile, Direction.NORTH)
        npc.start("movement_delay", -1)
    }
}
