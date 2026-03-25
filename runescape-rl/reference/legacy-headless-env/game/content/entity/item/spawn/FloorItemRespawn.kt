package content.entity.item.spawn

import sim.engine.Script
import sim.engine.entity.item.floor.FloorItem
import sim.engine.entity.item.floor.FloorItems
import sim.engine.entity.item.floor.ItemSpawns

class FloorItemRespawn(val spawns: ItemSpawns) : Script {

    init {
        floorItemDespawn {
            if (isSpawnItem(this)) {
                val spawn = spawns.get(tile) ?: return@floorItemDespawn
                FloorItems.add(tile, spawn.id, spawn.amount, revealTicks = spawn.delay, owner = "")
            }
        }
    }

    fun isSpawnItem(item: FloorItem): Boolean {
        val spawn = spawns.get(item.tile) ?: return false
        return item.id == spawn.id && item.amount == spawn.amount && item.owner == null
    }
}
