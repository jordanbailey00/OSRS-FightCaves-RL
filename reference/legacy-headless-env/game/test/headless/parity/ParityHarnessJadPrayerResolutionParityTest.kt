import content.area.karamja.tzhaar_city.JAD_HIT_TARGET_QUEUE_TICKS
import content.area.karamja.tzhaar_city.JAD_HIT_RESOLVE_OFFSET_TICKS
import org.junit.jupiter.api.Test
import sim.engine.entity.character.npc.NPCs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class ParityHarnessJadPrayerResolutionParityTest {

    @Test
    fun `prayer sampled before queued hit construction stays protective in oracle and headless even if toggled off later`() {
        val harness = ParityHarness()
        val result =
            harness.runAndCompare(
                seed = 63_071L,
                actionTrace =
                    listOf(
                        HeadlessReplayStep(
                            HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMagic),
                            ticksAfter = JAD_HIT_TARGET_QUEUE_TICKS,
                        ),
                        HeadlessReplayStep(
                            HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMagic),
                            ticksAfter = JAD_HIT_RESOLVE_OFFSET_TICKS - JAD_HIT_TARGET_QUEUE_TICKS,
                        ),
                    ),
                startWave = 1,
                playerName = "parity-jad-prayer-protected",
                settingsOverrides = parityTestOverrides(),
                configurePlayer = { player -> player["auto_retaliate"] = true },
                stepHook =
                    ParityStepHook { _, stepIndex, _, player ->
                        if (stepIndex == -1) {
                            NPCs.clear()
                            spawnJadWithTelegraphedAttack(player, attackId = "magic")
                        }
                    },
            )

        val oracleSnapshot = result.oracle.snapshots.last()
        val headlessSnapshot = result.headless.snapshots.last()
        val oracleTelegraph = assertNotNull(oracleSnapshot.jadTelegraph)
        val headlessTelegraph = assertNotNull(headlessSnapshot.jadTelegraph)
        assertEquals(oracleTelegraph, headlessTelegraph)
        assertEquals("protect_from_magic", oracleTelegraph.sampledProtectionPrayer)
        assertTrue(oracleTelegraph.protectedAtPrayerCheck)
        assertEquals(0, oracleTelegraph.resolvedDamage)
        assertTrue(!oracleSnapshot.observation.player.protectionPrayers.protectFromMagic)
        assertTrue(!headlessSnapshot.observation.player.protectionPrayers.protectFromMagic)
    }

    @Test
    fun `toggling prayer after the queued hit is committed is too late in both oracle and headless`() {
        val harness = ParityHarness()
        val result =
            harness.runAndCompare(
                seed = 63_072L,
                actionTrace =
                    listOf(
                        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = JAD_HIT_RESOLVE_OFFSET_TICKS - 1),
                        HeadlessReplayStep(HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMissiles), ticksAfter = 1),
                    ),
                startWave = 1,
                playerName = "parity-jad-prayer-too-late",
                settingsOverrides = parityTestOverrides(),
                configurePlayer = { player -> player["auto_retaliate"] = true },
                stepHook =
                    ParityStepHook { _, stepIndex, _, player ->
                        if (stepIndex == -1) {
                            NPCs.clear()
                            spawnJadWithTelegraphedAttack(player, attackId = "range")
                        }
                    },
            )

        val oracleSnapshot = result.oracle.snapshots.last()
        val headlessSnapshot = result.headless.snapshots.last()
        val oracleTelegraph = assertNotNull(oracleSnapshot.jadTelegraph)
        val headlessTelegraph = assertNotNull(headlessSnapshot.jadTelegraph)
        assertEquals("none", oracleTelegraph.sampledProtectionPrayer)
        assertEquals("none", headlessTelegraph.sampledProtectionPrayer)
        assertTrue(!oracleTelegraph.protectedAtPrayerCheck)
        assertTrue(!headlessTelegraph.protectedAtPrayerCheck)
        assertTrue(oracleTelegraph.resolvedDamage > 0)
        assertTrue(headlessTelegraph.resolvedDamage > 0)
        assertTrue(oracleSnapshot.observation.player.protectionPrayers.protectFromMissiles)
        assertTrue(headlessSnapshot.observation.player.protectionPrayers.protectFromMissiles)
    }
}
