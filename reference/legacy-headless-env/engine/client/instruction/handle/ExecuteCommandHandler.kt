package sim.engine.client.instruction.handle

import sim.engine.Script
import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.command.Commands
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.player.Player
import sim.engine.event.AuditLog
import sim.network.client.instruction.ExecuteCommand

class ExecuteCommandHandler : InstructionHandler<ExecuteCommand>() {

    override fun validate(player: Player, instruction: ExecuteCommand): Boolean {
        if (instruction.tab) {
            Commands.autofill(player, instruction.command)
            return true
        }
        val parts = instruction.command.split(" ")
        val prefix = parts[0]
        val content = instruction.command.removePrefix(prefix).trim()
        if (instruction.automatic) {
            val params = content.split(",")
            val level = params[0].toInt()
            val x = params[1].toInt() shl 6 or params[3].toInt()
            val y = params[2].toInt() shl 6 or params[4].toInt()
            player.tele(x, y, level)
            player["world_map_centre"] = player.tile.id
            player["world_map_marker_player"] = player.tile.id
            return true
        }
        Script.launch {
            AuditLog.event(player, "command", "\"${instruction.command}\"")
            Commands.call(player, instruction.command)
        }
        return true
    }
}
