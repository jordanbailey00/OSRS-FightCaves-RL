package content.social.assist

import sim.engine.Script
import sim.engine.client.ui.closeMenu
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.temporaryMoveType
import sim.network.login.protocol.visual.update.player.MoveType
import sim.types.Tile

class AssistArea : Script {

    val maximumTileDistance = 20

    init {
        moved(::leavingArea)
    }

    /**
     * Player leaving assistance range
     */
    fun leavingArea(player: Player, from: Tile) {
        if (!player.contains("assistant")) {
            return
        }
        when (player.temporaryMoveType) {
            MoveType.Teleport -> player["assist_point"] = player.tile
            else -> {
                val point: Tile? = player["assist_point"]
                if (point == null || !player.tile.within(point, maximumTileDistance)) {
                    val assistant: Player? = player["assistant"]
                    assistant?.closeMenu()
                }
            }
        }
    }
}
