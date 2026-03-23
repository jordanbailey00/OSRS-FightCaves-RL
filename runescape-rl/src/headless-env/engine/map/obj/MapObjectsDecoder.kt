package sim.engine.map.obj

import sim.cache.Cache
import sim.cache.Index
import sim.cache.definition.decoder.MapObjectDecoder
import sim.engine.data.definition.ObjectDefinitions
import sim.engine.entity.obj.GameObjects

/**
 * Adds collision for all blocked tiles except bridges
 */
class MapObjectsDecoder : MapObjectDecoder() {

    fun decode(cache: Cache, settings: ByteArray, regionX: Int, regionY: Int, keys: IntArray?) {
        val objectData = cache.data(Index.MAPS, "l${regionX}_$regionY", xtea = keys) ?: return
        val zoneTileX = regionX shl 6
        val zoneTileY = regionY shl 6
        super.decode(objectData, settings, zoneTileX, zoneTileY)
    }

    override fun add(objectId: Int, localX: Int, localY: Int, level: Int, shape: Int, rotation: Int, regionTileX: Int, regionTileY: Int) {
        // 202, 75
        GameObjects.set(objectId, regionTileX + localX, regionTileY + localY, level, shape, rotation, ObjectDefinitions.getValue(objectId))
    }
}
