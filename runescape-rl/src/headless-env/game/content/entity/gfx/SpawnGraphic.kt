package content.entity.gfx

import sim.engine.client.update.batch.ZoneBatchUpdates
import sim.engine.data.definition.GraphicDefinitions
import sim.engine.get
import sim.network.login.protocol.encode.zone.GraphicAddition
import sim.types.Direction
import sim.types.Tile

fun areaGfx(
    id: String,
    tile: Tile,
    delay: Int = 0,
    height: Int = 0,
    rotation: Direction = Direction.SOUTH,
) {
    ZoneBatchUpdates.add(tile.zone, GraphicAddition(tile.id, get<GraphicDefinitions>().get(id).id, height, delay, rotation.ordinal))
}
