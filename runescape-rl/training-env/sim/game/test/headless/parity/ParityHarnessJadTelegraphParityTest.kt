import content.area.karamja.tzhaar_city.JAD_HIT_RESOLVE_OFFSET_TICKS
import content.area.karamja.tzhaar_city.beginJadTelegraphForAttack
import org.junit.jupiter.api.Test
import sim.engine.entity.character.npc.NPCs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class ParityHarnessJadTelegraphParityTest {

    @Test
    fun `jad telegraph trace and headless cue stay aligned between oracle and headless`() {
        val harness = ParityHarness()
        val result =
            harness.runAndCompare(
                seed = 63_063L,
                actionTrace = emptyList(),
                startWave = 1,
                playerName = "parity-jad-telegraph",
                settingsOverrides = parityTestOverrides(),
                configurePlayer = ::parityPlayerSetup,
                stepHook =
                    ParityStepHook { _, stepIndex, _, player ->
                        if (stepIndex != -1) {
                            return@ParityStepHook
                        }
                        val jad = NPCs.add("tztok_jad", player.tile.add(1, 0))
                        NPCs.run()
                        assertTrue(jad.beginJadTelegraphForAttack("range"))
                    },
            )

        result.requirePassed()

        val oracleSnapshot = result.oracle.snapshots.single()
        val headlessSnapshot = result.headless.snapshots.single()
        val oracleTelegraph = assertNotNull(oracleSnapshot.jadTelegraph)
        val headlessTelegraph = assertNotNull(headlessSnapshot.jadTelegraph)

        assertEquals(oracleTelegraph, headlessTelegraph)
        assertEquals("ranged_windup", oracleTelegraph.telegraphState)
        assertEquals("range", oracleTelegraph.committedAttackStyle)
        assertEquals(JAD_HIT_RESOLVE_OFFSET_TICKS, oracleTelegraph.hitResolveTick - oracleTelegraph.telegraphStartTick)
        assertEquals(2, oracleSnapshot.observation.npcs.first { it.id == "tztok_jad" }.jadTelegraphState)
        assertEquals(2, headlessSnapshot.observation.npcs.first { it.id == "tztok_jad" }.jadTelegraphState)
    }
}
