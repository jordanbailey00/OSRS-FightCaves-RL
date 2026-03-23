import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals

internal class EpisodeInitUsesProvidedSeedTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `episode init uses provided seed for wave rotation`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-episode-seed")

        val firstSeed = 101L
        val secondSeed = 202L

        val first = runtime.resetFightCaveEpisode(player, FightCaveEpisodeConfig(seed = firstSeed, startWave = 1))
        val second = runtime.resetFightCaveEpisode(player, FightCaveEpisodeConfig(seed = secondSeed, startWave = 1))
        val third = runtime.resetFightCaveEpisode(player, FightCaveEpisodeConfig(seed = firstSeed, startWave = 1))

        assertEquals(firstSeed, first.seed)
        assertEquals(secondSeed, second.seed)
        assertEquals(firstSeed, third.seed)

        assertEquals(expectedRotation(firstSeed), first.rotation)
        assertEquals(expectedRotation(secondSeed), second.rotation)
        assertEquals(expectedRotation(firstSeed), third.rotation)

        assertEquals(firstSeed, player["episode_seed", -1L])
    }

    private fun expectedRotation(seed: Long): Int = Random(seed).nextInt(1, 16)
}
