package content.entity.player.command

import sim.engine.Script
import sim.engine.client.command.adminCommand
import sim.engine.client.command.intArg
import sim.engine.map.zone.DynamicZones
import sim.types.Zone

class ZoneCommands(val zones: DynamicZones) : Script {
    init {
        adminCommand("rotate_zone", intArg("rotation", optional = true), desc = "Rotate the current zone") { args ->
            zones.copy(tile.zone, tile.zone, rotation = args.getOrNull(0)?.toIntOrNull() ?: 1)
        }
        adminCommand("clear_zone", desc = "Reset the current zone back to static") { _ ->
            zones.clear(tile.zone)
        }
        adminCommand("copy_zone", intArg("from"), intArg("to", optional = true), intArg("rotation", optional = true), desc = "Create a dynamic zone copy") { args ->
            zones.copy(Zone(args[0].toInt()), args.getOrNull(1)?.toIntOrNull()?.let { Zone(it) } ?: tile.zone, rotation = args.getOrNull(2)?.toIntOrNull() ?: 0)
        }
    }
}
