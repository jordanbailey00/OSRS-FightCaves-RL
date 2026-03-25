import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.data.Settings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class HeadlessPackageStartsWithoutExcludedSystemsTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `headless package starts with excluded systems disabled`() {
        val runtime =
            HeadlessMain.bootstrap(
                loadContentScripts = true,
                startWorld = false,
                installShutdownHook = false,
                settingsOverrides = headlessTestOverrides(),
            )

        val excludedStages =
            setOf(
                "ConnectionQueue",
                "SaveQueue",
                "SaveLogs",
                "BotManager",
                "Hunting",
                "GrandExchange",
                "FloorItems",
            )
        val stageNames = describeTickStages(runtime.stages).toSet()
        assertTrue(stageNames.intersect(excludedStages).isEmpty(), "Headless stage pipeline should not include excluded systems.")

        assertEquals(0, Settings["bots.count", -1])
        assertFalse(Settings["events.shootingStars.enabled", true])
        assertFalse(Settings["events.penguinHideAndSeek.enabled", true])
        assertEquals(0, Settings["storage.autoSave.minutes", -1])
        assertEquals("tzhaar_city.npc-spawns.toml", Settings["spawns.npcs", ""])
        assertEquals("tzhaar_city.items.toml", Settings["spawns.items", ""])
        assertEquals("tzhaar_city.objs.toml", Settings["spawns.objects", ""])

        runtime.shutdown()
    }
}
