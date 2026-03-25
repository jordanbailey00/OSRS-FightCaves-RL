package content.area.karamja.tzhaar_city

import WorldTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JadTelegraphRegressionTest : WorldTest() {

    @Test
    fun `non jad attacks do not start jad telegraph state`() {
        val npc = createNPC("spiritual_mage_bandos")

        val started = npc.beginJadTelegraphForAttack("magic")

        assertFalse(started)
        assertEquals(JadTelegraphState.Idle, npc.jadTelegraphState)
        assertEquals(JadCommittedAttackStyle.None, npc.jadCommittedAttackStyle)
        assertEquals(-1, npc.jadTelegraphStartTick)
        assertEquals(-1, npc.jadHitResolveTick)
    }

    @Test
    fun `jad attacks still start jad telegraph state`() {
        val jad = createNPC("tztok_jad")

        val started = jad.beginJadTelegraphForAttack("magic")

        assertTrue(started)
        assertEquals(JadTelegraphState.MagicWindup, jad.jadTelegraphState)
        assertEquals(JadCommittedAttackStyle.Magic, jad.jadCommittedAttackStyle)
    }
}
