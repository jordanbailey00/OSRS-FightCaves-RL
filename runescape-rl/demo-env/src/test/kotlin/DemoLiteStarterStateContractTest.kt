import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DemoLiteStarterStateContractTest {
    @Test
    fun `headed demo-lite boot matches the canonical episode-start contract`() {
        withDemoLiteSession { session ->
            val state = session.reset()
            val context = session.currentEpisodeContextForValidation()
            val manifest = session.starterStateManifest()

            DemoLiteValidationArtifactTestSupport.writeJsonArtifact(
                "starter-state-manifest.json",
                manifest.toOrderedMap(),
            )

            assertEquals(1, manifest.startWaveRequested)
            assertEquals(context.episodeConfig.seed, manifest.episodeSeed)
            assertEquals(1, manifest.observedWave)
            assertEquals(context.episodeState.wave, manifest.observedWave)
            assertEquals(context.episodeState.rotation, manifest.observedRotation)
            assertEquals(context.episodeState.remaining, manifest.observedRemaining)
            assertEquals(context.episodeState.playerTile.x, manifest.observedPlayerTile.x)
            assertEquals(context.episodeState.playerTile.y, manifest.observedPlayerTile.y)
            assertEquals(context.episodeState.playerTile.level, manifest.observedPlayerTile.level)
            assertTrue(manifest.instanceId > 0)
            assertEquals(DemoLiteStarterStateManifest.expectedEquipment(context.episodeConfig), manifest.equipment)
            assertEquals(DemoLiteStarterStateManifest.expectedInventory(context.episodeConfig), manifest.inventory)
            assertEquals("adamant_bolts", manifest.ammoId)
            assertEquals(1_000, manifest.ammoCount)
            assertEquals(20, manifest.sharkCount)
            assertEquals(32, manifest.prayerPotionDoseCount)
            assertEquals(DemoLiteStarterStateManifest.expectedSkills().size, manifest.skills.size)
            for ((skillName, expectedLevel) in DemoLiteStarterStateManifest.expectedSkills()) {
                val skillEntry = assertNotNull(manifest.skills[skillName], "Missing manifest skill entry for '$skillName'.")
                assertEquals(expectedLevel, skillEntry.level, "Unexpected level for '$skillName'.")
                assertTrue(skillEntry.blocked, "Skill '$skillName' should be XP-blocked at episode start.")
            }
            assertEquals(70, manifest.hitpointsDisplay)
            assertEquals(700, manifest.constitutionCurrent)
            assertEquals(700, manifest.constitutionMax)
            assertEquals(43, manifest.prayerCurrent)
            assertEquals(43, manifest.prayerMax)
            assertEquals(100, manifest.runEnergyPercent)
            assertTrue(manifest.running)
            assertEquals("run", manifest.movementMode)
            assertTrue(manifest.skipLevelUp)
            assertTrue(manifest.noXpGainConfigured)
            assertEquals(Skill.count, manifest.blockedSkills.size)
            assertFalse(state.observation.debugFutureLeakage?.enabled ?: false)
        }
    }
}
