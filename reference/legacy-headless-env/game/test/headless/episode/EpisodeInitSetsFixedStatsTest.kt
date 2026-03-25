import content.entity.player.effect.energy.MAX_RUN_ENERGY
import content.entity.player.effect.energy.runEnergy
import content.skill.prayer.PrayerConfigs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.entity.character.move.running
import sim.engine.entity.character.player.skill.Skill
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class EpisodeInitSetsFixedStatsTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `episode init sets fixed stats resources and toggles`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-episode-stats")

        player.levels.set(Skill.Attack, 99)
        player.levels.set(Skill.Strength, 99)
        player.levels.set(Skill.Defence, 1)
        player.levels.set(Skill.Constitution, 100)
        player.levels.set(Skill.Ranged, 1)
        player.levels.set(Skill.Prayer, 1)
        player.levels.set(Skill.Magic, 99)
        player.experience.removeBlock(Skill.Attack)
        player.experience.add(Skill.Attack, 5000.0)
        player.runEnergy = 250
        player.running = false
        player[PrayerConfigs.PRAYERS] = "protect_magic"
        player[PrayerConfigs.USING_QUICK_PRAYERS] = true
        player[PrayerConfigs.SELECTING_QUICK_PRAYERS] = true
        player[PrayerConfigs.ACTIVE_PRAYERS] = setOf("protect_magic")
        player[PrayerConfigs.ACTIVE_CURSES] = setOf("leech_attack")
        player[PrayerConfigs.QUICK_PRAYERS] = setOf("protect_magic")
        player[PrayerConfigs.QUICK_CURSES] = setOf("leech_attack")
        player[PrayerConfigs.TEMP_QUICK_PRAYERS] = setOf("protect_magic")

        runtime.resetFightCaveEpisode(player, FightCaveEpisodeConfig(seed = 42L, startWave = 1))

        val expected =
            mapOf(
                Skill.Attack to 1,
                Skill.Strength to 1,
                Skill.Defence to 70,
                Skill.Constitution to 700,
                Skill.Ranged to 70,
                Skill.Prayer to 43,
                Skill.Magic to 1,
            )

        for (skill in Skill.all) {
            val level = expected[skill] ?: 1
            assertEquals(level, player.levels.get(skill), "Current level mismatch for $skill")
            assertEquals(level, player.levels.getMax(skill), "Max level mismatch for $skill")
            assertTrue(player.experience.blocked(skill), "Expected XP to be blocked for $skill")
        }

        assertEquals(MAX_RUN_ENERGY, player.runEnergy)
        assertTrue(player.running)
        assertEquals("run", player["movement", "walk"])

        assertEquals("", player[PrayerConfigs.PRAYERS, "x"])
        assertFalse(player[PrayerConfigs.USING_QUICK_PRAYERS, true])
        assertFalse(player[PrayerConfigs.SELECTING_QUICK_PRAYERS, true])
        assertFalse(player.contains(PrayerConfigs.ACTIVE_PRAYERS))
        assertFalse(player.contains(PrayerConfigs.ACTIVE_CURSES))
        assertFalse(player.contains(PrayerConfigs.QUICK_PRAYERS))
        assertFalse(player.contains(PrayerConfigs.QUICK_CURSES))
        assertFalse(player.contains(PrayerConfigs.TEMP_QUICK_PRAYERS))
        assertFalse(player.contains("prayer_drain_counter"))
    }
}
