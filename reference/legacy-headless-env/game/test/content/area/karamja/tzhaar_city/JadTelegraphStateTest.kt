import content.area.karamja.tzhaar_city.JAD_HIT_RESOLVE_OFFSET_TICKS
import content.area.karamja.tzhaar_city.JadCommittedAttackStyle
import content.area.karamja.tzhaar_city.JadTelegraphState
import content.area.karamja.tzhaar_city.beginJadTelegraphForAttack
import content.area.karamja.tzhaar_city.jadCommittedAttackStyle
import content.area.karamja.tzhaar_city.jadCommittedAttackStyleForAttackId
import content.area.karamja.tzhaar_city.jadHitResolveTick
import content.area.karamja.tzhaar_city.jadTelegraphStartTick
import content.area.karamja.tzhaar_city.jadTelegraphState
import content.area.karamja.tzhaar_city.jadTelegraphStateForStyle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.GameLoop
import sim.engine.entity.character.npc.NPCs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class JadTelegraphStateTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `jad telegraph timing contract preserves current headed reaction window`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("jad-telegraph-state")
        val jad = NPCs.add("tztok_jad", player.tile)
        NPCs.run()
        val onsetTick = GameLoop.tick

        assertTrue(jad.beginJadTelegraphForAttack("magic"))
        assertEquals(JadCommittedAttackStyle.Magic, jad.jadCommittedAttackStyle)
        assertEquals(JadTelegraphState.MagicWindup, jad.jadTelegraphState)
        assertEquals(onsetTick, jad.jadTelegraphStartTick)
        assertEquals(onsetTick + JAD_HIT_RESOLVE_OFFSET_TICKS, jad.jadHitResolveTick)

        runtime.tick(JAD_HIT_RESOLVE_OFFSET_TICKS - 1)
        assertEquals(JadTelegraphState.MagicWindup, jad.jadTelegraphState)

        runtime.tick(1)
        assertEquals(JadTelegraphState.Idle, jad.jadTelegraphState)
        assertEquals(JadCommittedAttackStyle.None, jad.jadCommittedAttackStyle)
        assertEquals(-1, jad.jadTelegraphStartTick)
        assertEquals(-1, jad.jadHitResolveTick)
    }

    @Test
    fun `jad telegraph only applies to real jad style commits`() {
        assertEquals(JadCommittedAttackStyle.Magic, jadCommittedAttackStyleForAttackId("magic"))
        assertEquals(JadCommittedAttackStyle.Ranged, jadCommittedAttackStyleForAttackId("range"))
        assertEquals(JadTelegraphState.MagicWindup, jadTelegraphStateForStyle(JadCommittedAttackStyle.Magic))
        assertEquals(JadTelegraphState.RangedWindup, jadTelegraphStateForStyle(JadCommittedAttackStyle.Ranged))
        assertFalse(listOf("melee", "", "unknown").any { jadCommittedAttackStyleForAttackId(it) != null })
    }
}
