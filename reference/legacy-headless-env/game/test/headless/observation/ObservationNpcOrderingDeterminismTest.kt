import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.entity.character.npc.NPCs
import sim.types.Tile
import kotlin.test.assertEquals

internal class ObservationNpcOrderingDeterminismTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `observation npc ordering is deterministic and aligned with visible indices`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-observation-order", tile = Tile(3000, 3000, 3))

        val npcWest = NPCs.add("tz_kih", player.tile.add(1, 0))
        val npcMid = NPCs.add("tok_xil", player.tile.add(2, 0))
        val npcEast = NPCs.add("yt_mej_kot", player.tile.add(3, 0))
        NPCs.run()

        val first = runtime.observeFightCave(player)
        val second = runtime.observeFightCave(player)

        assertEquals(first.npcs.map { it.npcIndex }, second.npcs.map { it.npcIndex })
        assertEquals((0 until first.npcs.size).toList(), first.npcs.map { it.visibleIndex })
        assertEquals(listOf(npcWest.index, npcMid.index, npcEast.index), first.npcs.take(3).map { it.npcIndex })
    }
}
