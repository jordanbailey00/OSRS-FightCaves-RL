import content.area.karamja.tzhaar_city.TzhaarFightCave
import content.quest.instanceOffset
import content.quest.smallInstance
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.entity.character.move.tele
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class HeadlessSingleWaveScriptSmokeTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `single wave run executes fight cave script hooks`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-wave-smoke")

        val fightCave = runtime.loadedScripts.filterIsInstance<TzhaarFightCave>().singleOrNull()
        assertNotNull(fightCave, "Expected TzhaarFightCave script instance to be loaded in headless runtime.")

        player.smallInstance(fightCave.region, levels = 3)
        val offset = player.instanceOffset()
        player.tele(fightCave.entrance.add(offset))
        player.walkTo(fightCave.centre.add(offset))

        fightCave.startWave(player, wave = 1, start = true)
        runtime.tick(2)

        assertEquals(1, player["fight_cave_wave", -1])
        assertTrue(player["fight_cave_remaining", 0] > 0)
        assertTrue(player.contains("fight_cave_rotation"))
        assertTrue(player.contains("fight_cave_start_time"))

        runtime.tick()
    }
}
