import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.map.collision.Collisions
import sim.types.Zone
import kotlin.test.assertTrue

internal class HeadlessCollisionRegionSubsetTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `headless map collision loading is limited to allowlisted regions`() {
        val runtime = HeadlessMain.bootstrap(
            loadContentScripts = false,
            startWorld = false,
            installShutdownHook = false,
            settingsOverrides = headlessTestOverrides(),
        )

        val allowedRegions = runtime.allowlist.requiredSourceRegions.toSet()
        assertTrue(allowedRegions.isNotEmpty(), "Allowlisted source regions must not be empty.")

        val loadedRegions = mutableSetOf<Int>()
        for (index in Collisions.map.flags.indices) {
            if (Collisions.map.flags[index] != null) {
                loadedRegions += Zone(index).region.id
            }
        }

        assertTrue(loadedRegions.isNotEmpty(), "Expected collision map to load at least one region.")
        assertTrue(loadedRegions.all { it in allowedRegions }, "Unexpected collision regions loaded: ${loadedRegions - allowedRegions}")
        assertTrue(loadedRegions.any { it in allowedRegions }, "No allowlisted regions were loaded into collision map.")

        runtime.tick()
    }
}
