import content.area.karamja.tzhaar_city.JAD_HIT_RESOLVE_OFFSET_TICKS
import content.area.karamja.tzhaar_city.beginJadTelegraphForAttack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.entity.character.npc.NPCs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ObservationJadTelegraphStateTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `headless observation exposes jad telegraph state only during real windup`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-observation-jad-telegraph")
        val jad = NPCs.add("tztok_jad", player.tile.add(1, 0))
        NPCs.run()

        val initialObservation = runtime.observeFightCave(player)
        val initialJad = initialObservation.npcs.first { it.id == "tztok_jad" }
        assertEquals(0, initialJad.jadTelegraphState)
        assertTrue(jad.beginJadTelegraphForAttack("magic"))

        val magicObservation = runtime.observeFightCave(player)
        val magicJad = magicObservation.npcs.first { it.id == "tztok_jad" }
        assertEquals(1, magicJad.jadTelegraphState)
        assertEquals(1, magicJad.toOrderedMap()["jad_telegraph_state"])

        runtime.tick(JAD_HIT_RESOLVE_OFFSET_TICKS)
        val clearedObservation = runtime.observeFightCave(player)
        val clearedJad = clearedObservation.npcs.first { it.id == "tztok_jad" }
        assertEquals(0, clearedJad.jadTelegraphState)
    }

    @Test
    fun `headless observation distinguishes ranged jad windup without exposing an oracle`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-observation-jad-telegraph-range")
        val jad = NPCs.add("tztok_jad", player.tile.add(1, 0))
        val filler = NPCs.add("tz_kih", player.tile.add(2, 0))
        NPCs.run()

        assertTrue(jad.beginJadTelegraphForAttack("range"))
        val observation = runtime.observeFightCave(player)
        val jadEntry = observation.npcs.first { it.id == "tztok_jad" }
        val fillerEntry = observation.npcs.first { it.npcIndex == filler.index }

        assertEquals(2, jadEntry.jadTelegraphState)
        assertEquals(0, fillerEntry.jadTelegraphState)
        assertEquals(
            listOf(
                "visible_index",
                "npc_index",
                "id",
                "tile",
                "hitpoints_current",
                "hitpoints_max",
                "hidden",
                "dead",
                "under_attack",
                "jad_telegraph_state",
            ),
            jadEntry.toOrderedMap().keys.toList(),
        )
    }

}
