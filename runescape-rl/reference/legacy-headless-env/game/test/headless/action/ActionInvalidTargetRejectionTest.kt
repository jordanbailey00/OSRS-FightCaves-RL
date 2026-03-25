import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.types.Tile
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class ActionInvalidTargetRejectionTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `attack action rejects invalid visible npc index`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-action-invalid-target", tile = Tile(3000, 3000, 3))

        val result = runtime.applyFightCaveAction(player, HeadlessAction.AttackVisibleNpc(0))

        assertFalse(result.actionApplied)
        assertEquals(HeadlessActionRejectReason.InvalidTargetIndex, result.rejectionReason)
        assertEquals("0", result.metadata["visible_npc_count"])
        assertEquals("false", result.metadata["action_applied"])
    }
}
