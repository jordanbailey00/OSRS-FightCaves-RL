package sim.engine.map.obj

import sim.cache.Cache
import sim.cache.Index
import sim.cache.definition.decoder.MapObjectDecoder
import sim.engine.data.definition.ObjectDefinitions
import sim.engine.entity.obj.GameObjects
import sim.types.Zone
import sim.types.area.Rectangle

/**
 * Adds all objects except bridges from a single [zone], into a different zone, with [zoneRotation] applied.
 */
class MapObjectsRotatedDecoder : MapObjectDecoder() {

    internal var zoneRotation: Int = 0
    internal lateinit var zone: Rectangle

    fun decode(cache: Cache, settings: ByteArray, from: Zone, to: Zone, rotation: Int, keys: IntArray?) {
        val objectData = cache.data(Index.MAPS, "l${from.region.x}_${from.region.y}", xtea = keys) ?: return
        val x = from.tile.x.rem(64)
        val y = from.tile.y.rem(64)
        zone = Rectangle(x, y, x + 7, y + 7)
        zoneRotation = rotation
        super.decode(objectData, settings, to.tile.x, to.tile.y)
    }

    override fun add(objectId: Int, localX: Int, localY: Int, level: Int, shape: Int, rotation: Int, regionTileX: Int, regionTileY: Int) {
        if (objectId > ObjectDefinitions.definitions.size || !zone.contains(localX, localY)) {
            return
        }
        val def = ObjectDefinitions.getValue(objectId)
        val objRotation = (rotation + zoneRotation) and 0x3
        val rotX = rotateX(localX.rem(8), localY.rem(8), def.sizeX, def.sizeY, objRotation, zoneRotation)
        val rotY = rotateY(localX.rem(8), localY.rem(8), def.sizeX, def.sizeY, objRotation, zoneRotation)
        GameObjects.set(objectId, regionTileX + rotX, regionTileY + rotY, level, shape, objRotation, def)
    }

    internal fun rotateX(
        objX: Int,
        objY: Int,
        sizeX: Int,
        sizeY: Int,
        objRotation: Int,
        zoneRotation: Int,
    ): Int {
        var x = sizeX
        var y = sizeY
        val rotation = zoneRotation and 0x3
        if (objRotation and 0x1 == 1) {
            val temp = x
            x = y
            y = temp
        }
        if (rotation == 0) {
            return objX
        }
        if (rotation == 1) {
            return objY
        }
        return if (rotation == 2) 7 - objX - x + 1 else 7 - objY - y + 1
    }

    internal fun rotateY(
        objX: Int,
        objY: Int,
        sizeX: Int,
        sizeY: Int,
        objRotation: Int,
        zoneRotation: Int,
    ): Int {
        val rotation = zoneRotation and 0x3
        var x = sizeY
        var y = sizeX
        if (objRotation and 0x1 == 1) {
            val temp = y
            y = x
            x = temp
        }
        if (rotation == 0) {
            return objY
        }
        if (rotation == 1) {
            return 7 - objX - y + 1
        }
        return if (rotation == 2) 7 - objY - x + 1 else objX
    }
}
