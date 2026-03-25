package sim.cache.definition.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sim.cache.definition.data.MapTile.Companion.height
import sim.cache.definition.data.MapTile.Companion.opcode
import sim.cache.definition.data.MapTile.Companion.overlay
import sim.cache.definition.data.MapTile.Companion.pack
import sim.cache.definition.data.MapTile.Companion.path
import sim.cache.definition.data.MapTile.Companion.rotation
import sim.cache.definition.data.MapTile.Companion.settings
import sim.cache.definition.data.MapTile.Companion.underlay

internal class MapTileTest {

    @Test
    fun `Get values from packed`() {
        val packed = pack(255, 49, 255, 11, 4, 32, 174)
        assertEquals(255, height(packed))
        assertEquals(49, opcode(packed))
        assertEquals(255, overlay(packed))
        assertEquals(11, path(packed))
        assertEquals(4, rotation(packed))
        assertEquals(32, settings(packed))
        assertEquals(174, underlay(packed))
    }
}
