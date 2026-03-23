import content.area.karamja.tzhaar_city.JAD_HIT_RESOLVE_OFFSET_TICKS
import content.skill.prayer.getActivePrayerVarKey
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class HeadlessReplayJadTelegraphTraceTest {

    @Test
    fun `replay snapshots capture jad telegraph timing from shared state`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        try {
            val player = createHeadlessPlayer("replay-jad-telegraph")
            val runner = HeadlessReplayRunner(runtime)
            val result =
                runner.run(
                    player = player,
                    seed = 630_031L,
                    actionTrace = emptyList(),
                    startWave = 1,
                    stepHook = { stepIndex, _, currentPlayer ->
                        if (stepIndex == -1) {
                            spawnJadWithTelegraphedAttack(currentPlayer, attackId = "magic")
                        }
                    },
                )

            val snapshot = result.snapshots.single()
            val telegraph = assertNotNull(snapshot.jadTelegraph)
            val jadObservation = snapshot.observation.npcs.first { it.id == "tztok_jad" }

            assertEquals("magic_windup", telegraph.telegraphState)
            assertEquals("magic", telegraph.committedAttackStyle)
            assertEquals(snapshot.tick, telegraph.telegraphStartTick)
            assertEquals(snapshot.tick + JAD_HIT_RESOLVE_OFFSET_TICKS, telegraph.hitResolveTick)
            assertEquals(1, jadObservation.jadTelegraphState)
        } finally {
            runtime.shutdown()
            resetHeadlessTestRuntime()
        }
    }

    @Test
    fun `replay snapshots carry Jad prayer outcome through hit resolution`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        try {
            val player = createHeadlessPlayer("replay-jad-telegraph-outcome")
            val runner = HeadlessReplayRunner(runtime)
            val result =
                runner.run(
                    player = player,
                    seed = 630_032L,
                    actionTrace =
                        listOf(
                            HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = JAD_HIT_RESOLVE_OFFSET_TICKS),
                        ),
                    startWave = 1,
                    stepHook = { stepIndex, _, currentPlayer ->
                        if (stepIndex == -1) {
                            currentPlayer.addVarbit(currentPlayer.getActivePrayerVarKey(), "protect_from_magic")
                            spawnJadWithTelegraphedAttack(currentPlayer, attackId = "magic")
                        }
                    },
                )

            val snapshot = result.snapshots.last()
            val telegraph = assertNotNull(snapshot.jadTelegraph)

            assertEquals("idle", telegraph.telegraphState)
            assertEquals("protect_from_magic", telegraph.sampledProtectionPrayer)
            assertTrue(telegraph.protectedAtPrayerCheck)
            assertEquals(0, telegraph.resolvedDamage)
            assertTrue(telegraph.prayerCheckTick in (telegraph.telegraphStartTick + 1)..telegraph.hitResolveTick)
        } finally {
            runtime.shutdown()
            resetHeadlessTestRuntime()
        }
    }
}
