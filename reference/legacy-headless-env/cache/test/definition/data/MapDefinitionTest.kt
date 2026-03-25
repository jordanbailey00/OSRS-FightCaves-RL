package sim.cache.definition.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sim.cache.definition.data.MapDefinition.Companion.index
import sim.cache.definition.data.MapDefinition.Companion.level
import sim.cache.definition.data.MapDefinition.Companion.localX
import sim.cache.definition.data.MapDefinition.Companion.localY

internal class MapDefinitionTest {

    @Test
    fun `Get values from index`() {
        val index = index(54, 63, 3)
        assertEquals(54, localX(index))
        assertEquals(63, localY(index))
        assertEquals(3, level(index))
    }
}
