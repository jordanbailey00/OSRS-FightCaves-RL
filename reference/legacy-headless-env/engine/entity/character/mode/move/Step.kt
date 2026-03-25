package sim.engine.entity.character.mode.move

import sim.engine.client.ui.chat.toInt
import sim.types.Tile

typealias Step = Tile

private fun id(tile: Int, noCollision: Boolean = false, noRun: Boolean = false) = tile + (noCollision.toInt() shl 30) + (noRun.toInt() shl 31)

private fun noCollision(id: Int) = id shr 30 and 0x1 == 1
private fun noRun(id: Int) = id shr 31 and 0x1 == 1

fun Tile.step(noCollision: Boolean, noRun: Boolean): Step = Tile(id(id and 0x3FFFFFFF, noCollision, noRun))

val Step.noCollision: Boolean
    get() = noCollision(id)

val Step.noRun: Boolean
    get() = noRun(id)
