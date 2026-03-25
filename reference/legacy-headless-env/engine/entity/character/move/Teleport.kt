package sim.engine.entity.character.move

import sim.engine.client.ui.closeInterfaces
import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.EmptyMode
import sim.engine.entity.character.mode.move.Movement
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.temporaryMoveType
import sim.engine.event.AuditLog
import sim.network.login.protocol.visual.update.player.MoveType
import sim.types.Area
import sim.types.Delta
import sim.types.Direction
import sim.types.Tile

fun Character.tele(x: Int = tile.x, y: Int = tile.y, level: Int = tile.level, clearInterfaces: Boolean = true) = tele(Delta(x - tile.x, y - tile.y, level - tile.level), clearInterfaces = clearInterfaces)

fun Character.tele(area: Area) = tele(area.random())

fun Character.tele(tile: Tile, clearMode: Boolean = true, clearInterfaces: Boolean = true) = tele(tile.delta(this.tile), clearMode, clearInterfaces)

fun Character.tele(delta: Delta, clearMode: Boolean = true, clearInterfaces: Boolean = true) {
    if (delta == Delta.EMPTY) {
        return
    }
    if (clearMode) {
        mode = EmptyMode
    }
    if (this is Player) {
        if (clearInterfaces) {
            closeInterfaces()
        }
        temporaryMoveType = MoveType.Teleport
        AuditLog.event(this, "teled", tile.add(delta))
    }
    steps.clear()
    steps.previous = tile.add(delta).add(Direction.WEST)
    visuals.tele = true
    Movement.move(this, delta)
}
