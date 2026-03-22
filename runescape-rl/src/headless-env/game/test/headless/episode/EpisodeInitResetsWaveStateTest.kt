import content.quest.instance
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.entity.character.npc.NPCs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class EpisodeInitResetsWaveStateTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `episode init resets wave variables and instance state`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-episode-wave")

        runtime.resetFightCaveEpisode(player, FightCaveEpisodeConfig(seed = 1337L, startWave = 6))
        runtime.tick(2)

        val previousInstance = checkNotNull(player.instance())
        val injectedNpc = NPCs.add("tz_kih", player.tile)
        NPCs.run()
        assertTrue(injectedNpc.index != -1)

        player["fight_cave_wave"] = 99
        player["fight_cave_remaining"] = 0
        player["fight_caves_logout_warning"] = true

        runtime.resetFightCaveEpisode(player, FightCaveEpisodeConfig(seed = 7331L, startWave = 1))
        runtime.tick(2)

        val currentInstance = checkNotNull(player.instance())

        assertNotEquals(previousInstance.id, currentInstance.id)
        assertEquals(1, player["fight_cave_wave", -1])
        assertTrue(player["fight_cave_remaining", 0] > 0)
        assertFalse(player["fight_caves_logout_warning", true])
        assertTrue(player.contains("fight_cave_rotation"))
        assertTrue(player.contains("fight_cave_start_time"))

        for (level in 0..3) {
            assertEquals(0, NPCs.at(previousInstance.toLevel(level)).size, "Expected previous instance level $level to be cleared.")
        }
    }
}
