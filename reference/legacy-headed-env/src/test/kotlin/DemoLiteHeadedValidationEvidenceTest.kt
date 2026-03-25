import content.quest.clearInstance
import content.quest.instance
import headless.fast.FIGHT_CAVE_REMAINING_KEY
import headless.fast.FIGHT_CAVE_WAVE_KEY
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoLiteHeadedValidationEvidenceTest {
    @Test
    fun `direct boot and constrained reset flows produce headed validation evidence`() {
        withDemoLiteSession { session ->
            val resetController = DemoLiteResetController(autoResetDelayMillis = 0L)
            val checks = mutableListOf<DemoLiteValidationCheck>()

            val boot = session.reset()
            val bootContext = session.currentEpisodeContextForValidation()
            val directBootPass =
                boot.terminalCode == DemoLiteTerminalCode.NONE &&
                    boot.observation.wave.wave == 1 &&
                    bootContext.player.instance() != null
            checks +=
                DemoLiteValidationCheck(
                    name = "direct_boot_to_fight_caves",
                    passed = directBootPass,
                    note = "Episode reset enters a live Fight Caves instance at wave ${boot.observation.wave.wave}.",
                )
            assertTrue(directBootPass)

            checks +=
                DemoLiteValidationCheck(
                    name = "constrained_runtime_scope",
                    passed =
                        DemoLiteScopePolicy.runtimeSettingsOverrides["spawns.npcs"] == "tzhaar_city.npc-spawns.toml" &&
                            DemoLiteScopePolicy.runtimeSettingsOverrides["spawns.items"] == "tzhaar_city.items.toml" &&
                            DemoLiteScopePolicy.runtimeSettingsOverrides["spawns.objects"] == "tzhaar_city.objs.toml" &&
                            DemoLiteScopePolicy.runtimeSettingsOverrides["bots.count"] == "0" &&
                            DemoLiteScopePolicy.runtimeSettingsOverrides["world.npcs.randomWalk"] == "false",
                    note = "Demo-lite runtime scope is constrained through tzhaar-only spawns plus disabled bots/random walk, and invalid-state exits are forced back into reset flow.",
                )
            assertTrue(checks.last().passed)

            val deathContext = session.currentEpisodeContextForValidation()
            deathContext.player.levels.set(Skill.Constitution, 0)
            val death = session.step(null)
            assertEquals(DemoLiteTerminalCode.PLAYER_DEATH, death.terminalCode)
            assertTrue(resetController.evaluate(death, nowMillis = 0L).resetNow)
            checks +=
                DemoLiteValidationCheck(
                    name = "death_reset_flow",
                    passed = true,
                    note = "Zeroed Constitution produces PLAYER_DEATH and immediate constrained reset eligibility.",
                )

            val afterDeathReset = session.reset()
            assertEquals(1, afterDeathReset.observation.wave.wave)

            val completionContext = session.currentEpisodeContextForValidation()
            completionContext.player[FIGHT_CAVE_WAVE_KEY] = 63
            completionContext.player[FIGHT_CAVE_REMAINING_KEY] = 0
            val completion = session.step(null)
            assertEquals(DemoLiteTerminalCode.CAVE_COMPLETE, completion.terminalCode)
            assertTrue(resetController.evaluate(completion, nowMillis = 0L).resetNow)
            checks +=
                DemoLiteValidationCheck(
                    name = "completion_reset_flow",
                    passed = true,
                    note = "Wave 63 with zero remaining NPCs produces CAVE_COMPLETE and immediate constrained reset eligibility.",
                )

            val afterCompletionReset = session.reset()
            assertEquals(1, afterCompletionReset.observation.wave.wave)

            val invalidContext = session.currentEpisodeContextForValidation()
            invalidContext.player.clearInstance()
            val invalid = session.step(null)
            assertEquals(DemoLiteTerminalCode.INVALID_STATE, invalid.terminalCode)
            assertTrue(resetController.evaluate(invalid, nowMillis = 0L).resetNow)
            checks +=
                DemoLiteValidationCheck(
                    name = "invalid_state_handling",
                    passed = true,
                    note = "Leaving the Fight Caves instance produces INVALID_STATE and immediate constrained reset eligibility.",
                )

            DemoLiteValidationArtifactTestSupport.writeChecklistArtifact(
                fileName = "headed-validation-checklist.json",
                title = "Demo Lite PR 7.1 Headed Validation",
                checks = checks,
            )
        }
    }
}
