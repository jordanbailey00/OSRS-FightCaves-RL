import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ActionEatDrinkLockoutRejectionTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `eat and drink actions reject while respective lockouts are active`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-action-consume")
        runtime.resetFightCaveEpisode(player, seed = 77L, startWave = 1)

        val firstEat = runtime.applyFightCaveActionAndTick(player, HeadlessAction.EatShark)
        assertTrue(firstEat.actionApplied)

        val blockedEat = runtime.applyFightCaveActionAndTick(player, HeadlessAction.EatShark)
        assertFalse(blockedEat.actionApplied)
        assertEquals(HeadlessActionRejectReason.ConsumptionLocked, blockedEat.rejectionReason)
        assertEquals("food_delay", blockedEat.metadata["lock"])

        val firstDrink = runtime.applyFightCaveActionAndTick(player, HeadlessAction.DrinkPrayerPotion)
        assertTrue(firstDrink.actionApplied)

        val blockedDrink = runtime.applyFightCaveActionAndTick(player, HeadlessAction.DrinkPrayerPotion)
        assertFalse(blockedDrink.actionApplied)
        assertEquals(HeadlessActionRejectReason.ConsumptionLocked, blockedDrink.rejectionReason)
        assertEquals("drink_delay", blockedDrink.metadata["lock"])
    }
}
