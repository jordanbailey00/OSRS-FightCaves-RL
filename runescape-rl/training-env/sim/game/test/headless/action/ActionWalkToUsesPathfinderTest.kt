import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.entity.character.mode.move.Movement
import sim.types.Tile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ActionWalkToUsesPathfinderTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `walk action uses canonical movement mode and path queue`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-action-walk", tile = Tile(2438, 5168))
        val destination = player.tile.add(2, 0)

        val result = runtime.applyFightCaveAction(player, HeadlessAction.WalkToTile(destination))

        assertTrue(result.actionApplied)
        assertEquals(HeadlessActionType.WalkToTile, result.actionType)
        assertEquals("true", result.metadata["action_applied"])
        assertEquals("pathfinder", result.metadata["movement_strategy"])
        assertTrue(player.mode is Movement)

        runtime.tick()

        assertEquals(destination, player.steps.destination)
    }
}
