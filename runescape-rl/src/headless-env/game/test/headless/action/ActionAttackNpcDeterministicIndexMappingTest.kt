import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.entity.character.mode.interact.PlayerOnNPCInteract
import sim.engine.entity.character.npc.NPCs
import sim.types.Tile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ActionAttackNpcDeterministicIndexMappingTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `attack action maps deterministic visible index to expected npc`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-action-attack", tile = Tile(3000, 3000, 3))

        val npcWest = NPCs.add("tz_kih", player.tile.add(1, 0))
        val npcMid = NPCs.add("tok_xil", player.tile.add(2, 0))
        val npcEast = NPCs.add("yt_mej_kot", player.tile.add(3, 0))
        NPCs.run()

        val firstMap = runtime.visibleFightCaveNpcTargets(player)
        val secondMap = runtime.visibleFightCaveNpcTargets(player)

        assertEquals(firstMap.map { it.npcIndex }, secondMap.map { it.npcIndex })
        assertEquals(listOf(npcWest.index, npcMid.index, npcEast.index), firstMap.take(3).map { it.npcIndex })

        val targetVisibleIndex = firstMap.first { it.npcIndex == npcMid.index }.visibleIndex
        val result = runtime.applyFightCaveAction(player, HeadlessAction.AttackVisibleNpc(targetVisibleIndex))

        assertTrue(result.actionApplied)
        assertEquals(HeadlessActionType.AttackVisibleNpc, result.actionType)
        assertEquals(npcMid.index.toString(), result.metadata["target_npc_index"])

        val mode = player.mode as PlayerOnNPCInteract
        assertEquals(npcMid.index, mode.target.index)
    }
}
