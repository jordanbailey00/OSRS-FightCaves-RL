package content.entity.player.command

import sim.engine.Script
import sim.engine.client.command.adminCommand
import sim.engine.client.command.intArg
import sim.engine.client.command.modCommand
import sim.engine.client.moveCamera
import sim.engine.client.shakeCamera
import sim.engine.client.turnCamera
import sim.network.login.protocol.encode.clearCamera
import sim.types.Tile

class CameraCommands : Script {

    init {
        modCommand("camera_reset", desc = "Reset camera to normal") {
            client?.clearCamera()
        }

        adminCommand("move_to", intArg("x"), intArg("y"), intArg("height"), intArg("c-speed"), intArg("v-speed"), desc = "Move camera to look at coordinates") { args ->
            val viewport = viewport!!
            val result = viewport.lastLoadZone.safeMinus(viewport.zoneRadius, viewport.zoneRadius)
            val local = Tile(args[0].toInt(), args[1].toInt()).minus(result.tile)
            println(local)
            moveCamera(local, args[2].toInt(), args[3].toInt(), args[4].toInt())
        }

        adminCommand("look_at", intArg("x"), intArg("y"), intArg("height"), intArg("c-speed"), intArg("v-speed"), desc = "Turn camera to look at coordinates") { args ->
            val viewport = viewport!!
            val result = viewport.lastLoadZone.safeMinus(viewport.zoneRadius, viewport.zoneRadius)
            val local = Tile(args[0].toInt(), args[1].toInt()).minus(result.tile)
            println(local)
            turnCamera(local, args[2].toInt(), args[3].toInt(), args[4].toInt())
        }

        adminCommand("shake", intArg("intensity"), intArg("type"), intArg("cycle"), intArg("movement"), intArg("speed"), desc = "Shake camera") { args ->
            shakeCamera(args[0].toInt(), args[1].toInt(), args[2].toInt(), args[3].toInt(), args[4].toInt())
        }
    }
}
