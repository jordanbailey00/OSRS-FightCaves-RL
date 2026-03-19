package sim.engine.entity.item.floor

import sim.engine.client.update.batch.ZoneBatchUpdates
import sim.engine.entity.character.player.Players
import sim.network.login.protocol.encode.zone.FloorItemReveal

/**
 * Removes or reveals items once a floor items countdown is complete.
 */
class FloorItemTracking : Runnable {
    private val removal = mutableListOf<FloorItem>()

    override fun run() {
        for ((_, zone) in FloorItems.data) {
            for ((_, list) in zone) {
                for (floorItem in list) {
                    if (floorItem.reveal()) {
                        val player = Players.find(floorItem.owner!!)
                        ZoneBatchUpdates.add(floorItem.tile.zone, FloorItemReveal(floorItem.tile.id, floorItem.def.id, floorItem.amount, player?.index ?: -1))
                        floorItem.owner = null
                    } else if (floorItem.remove()) {
                        removal.add(floorItem)
                    }
                }
            }
        }
        for (item in removal) {
            FloorItems.remove(item)
        }
        removal.clear()
    }
}
