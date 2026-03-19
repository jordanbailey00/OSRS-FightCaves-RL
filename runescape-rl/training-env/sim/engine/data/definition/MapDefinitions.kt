package sim.engine.data.definition

import com.github.michaelbull.logging.InlineLogger
import sim.cache.Cache
import sim.cache.Index
import sim.cache.definition.decoder.MapTileDecoder
import sim.engine.client.ui.chat.plural
import sim.engine.data.ConfigFiles
import sim.engine.data.Settings
import sim.engine.entity.obj.GameObjects
import sim.engine.map.collision.CollisionDecoder
import sim.engine.map.obj.MapObjectsDecoder
import sim.engine.map.obj.MapObjectsRotatedDecoder
import sim.types.Region
import sim.types.Zone
import java.io.File
import kotlin.system.exitProcess

/**
 * Loads map collision and objects fast and direct
 *
 *  Note: this is the only place we store the cache; for dynamic zone loading.
 */
class MapDefinitions(
    private val collisions: CollisionDecoder,
    private val cache: Cache,
) {
    private val logger = InlineLogger()

    private val decoder = MapObjectsDecoder()
    private val rotationDecoder = MapObjectsRotatedDecoder()

    fun load(configFiles: ConfigFiles, xteas: Map<Int, IntArray>? = null): MapDefinitions {
        try {
            if (!Settings["storage.caching.active", false]) {
                loadCache(xteas)
                return this
            }
            val path = Settings["storage.caching.path"]
            File(path).mkdirs()
            val objectsFile = File("${path}${Settings["storage.caching.objects"]}")
            val collisionsFile = File("${path}${Settings["storage.caching.collisions"]}")
            if (objectsFile.exists() && collisionsFile.exists() && !configFiles.cacheUpdate) {
                val start = System.currentTimeMillis()
                val zones = collisions.load(collisionsFile)
                GameObjects.load(objectsFile)
                logger.info { "Loaded all maps $zones zones ${GameObjects.size} ${"object".plural(GameObjects.size)} in ${System.currentTimeMillis() - start}ms" }
            } else {
                loadCache(xteas)
                val start = System.currentTimeMillis()
                collisions.save(collisionsFile)
                GameObjects.save(objectsFile)
                logger.info { "Cached maps in ${System.currentTimeMillis() - start}ms" }
            }
            return this
        } catch (e: ArrayIndexOutOfBoundsException) {
            logger.error(e) { "Error loading map definition; do you have the latest cache?" }
            exitProcess(1)
        }
    }

    private fun loadCache(xteas: Map<Int, IntArray>? = null) {
        val start = System.currentTimeMillis()
        var regions = 0
        val settings = ByteArray(16384)
        val restricted = restrictedRegions()

        if (restricted.isNotEmpty()) {
            for (regionId in restricted.sorted()) {
                val regionX = Region.x(regionId)
                val regionY = Region.y(regionId)
                if (!loadSettings(cache, regionX, regionY, settings)) {
                    logger.warn { "Unable to load restricted region $regionId (x=$regionX, y=$regionY); map data missing." }
                    continue
                }
                collisions.decode(settings, regionX shl 6, regionY shl 6)
                val keys = if (xteas != null) xteas[regionId] else null
                decoder.decode(cache, settings, regionX, regionY, keys)
                regions++
            }
            logger.info {
                "Loaded $regions restricted maps ${GameObjects.size} ${"object".plural(GameObjects.size)} in ${System.currentTimeMillis() - start}ms"
            }
            return
        }

        for (regionX in 0 until 256) {
            for (regionY in 0 until 256) {
                if (!loadSettings(cache, regionX, regionY, settings)) {
                    continue
                }
                collisions.decode(settings, regionX shl 6, regionY shl 6)
                val keys = if (xteas != null) xteas[Region.id(regionX, regionY)] else null
                decoder.decode(cache, settings, regionX, regionY, keys)
                regions++
            }
        }
        logger.info { "Loaded $regions maps ${GameObjects.size} ${"object".plural(GameObjects.size)} in ${System.currentTimeMillis() - start}ms" }
    }

    fun loadZone(from: Zone, to: Zone, rotation: Int, xteas: Map<Int, IntArray>? = null) {
        val start = System.currentTimeMillis()
        val settings = loadSettings(cache, from.region.x, from.region.y) ?: return
        collisions.decode(settings, from, to, rotation)
        val keys = if (xteas != null) xteas[from.region.id] else null
        rotationDecoder.decode(cache, settings, from, to, rotation, keys)
        val took = System.currentTimeMillis() - start
        if (took > 5) {
            logger.info { "Loaded zone $from -> $to $rotation in ${took}ms" }
        }
    }

    private fun loadSettings(cache: Cache, regionX: Int, regionY: Int, settings: ByteArray): Boolean {
        val archive = cache.archiveId(Index.MAPS, "m${regionX}_$regionY")
        if (archive == -1) {
            return false
        }
        val data = cache.data(Index.MAPS, archive) ?: return false
        MapTileDecoder.loadTiles(data, settings)
        return true
    }



    private fun restrictedRegions(): Set<Int> {
        if (Settings["runtime.mode", "headed"] != "headless") {
            return emptySet()
        }
        val raw = Settings.getOrNull("headless.map.regions")?.trim().orEmpty()
        if (raw.isEmpty()) {
            return emptySet()
        }
        return raw
            .split(',')
            .map { value ->
                value.trim().toIntOrNull()
                    ?: throw IllegalStateException("Invalid region id '$value' in setting headless.map.regions='$raw'.")
            }
            .toSet()
    }

    private fun loadSettings(cache: Cache, regionX: Int, regionY: Int): ByteArray? {
        val archive = cache.archiveId(Index.MAPS, "m${regionX}_$regionY")
        if (archive == -1) {
            return null
        }
        val settings = ByteArray(16384)
        val data = cache.data(Index.MAPS, archive) ?: return null
        MapTileDecoder.loadTiles(data, settings)
        return settings
    }

}
