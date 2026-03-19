import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.data.Settings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FightCavesDemoSettingsTest {
    @AfterEach
    fun clearSettings() {
        Settings.clear()
    }

    @Test
    fun `fight caves demo profile overrides default settings`() {
        Settings.clear()
        Settings.load(DEFAULT_PROPERTY_FILE)
        Settings.load(FIGHT_CAVES_DEMO_PROPERTY_FILE)

        assertEquals("Void Fight Caves Demo", Settings["server.name"])
        assertFalse(Settings["world.start.creation", true])
        assertEquals(0, Settings["bots.count", -1])
        assertTrue(Settings["fightCave.demo.enabled", false])
        assertTrue(Settings["fightCave.demo.skipBotTick", false])
        assertEquals(1000, Settings["fightCave.demo.ammo", -1])
        assertEquals(8, Settings["fightCave.demo.prayerPotions", -1])
        assertEquals(20, Settings["fightCave.demo.sharks", -1])
        assertEquals("./data/fight_caves_demo/saves/", Settings["storage.players.path"])
        assertEquals("./data/fight_caves_demo/logs/", Settings["storage.players.logs"])
        assertEquals("./data/fight_caves_demo/errors/", Settings["storage.players.errors"])
    }
}
