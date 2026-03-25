import content.quest.instance
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.entity.character.npc.NPCs
import kotlin.test.assertTrue

internal class EpisodeInitLoadsWavesWithoutWorldStartTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `episode init loads fight cave waves when world start is disabled`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = false)
        val player = createHeadlessPlayer("headless-episode-waves")

        runtime.resetFightCaveEpisode(player, FightCaveEpisodeConfig(seed = 9001L, startWave = 1))
        assertTrue(player["fight_cave_remaining", 0] > 0)

        runtime.tick()
        val instance = checkNotNull(player.instance())
        val spawned = (0..3).sumOf { level -> NPCs.at(instance.toLevel(level)).size }
        assertTrue(spawned > 0)

        runtime.shutdown()
    }
}
