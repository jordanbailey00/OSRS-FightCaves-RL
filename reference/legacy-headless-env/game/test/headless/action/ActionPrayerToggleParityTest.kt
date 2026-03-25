import content.skill.prayer.getActivePrayerVarKey
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ActionPrayerToggleParityTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `prayer toggle action follows protection exclusivity and toggle parity`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-action-prayer")
        runtime.resetFightCaveEpisode(player, seed = 55L, startWave = 1)

        val key = player.getActivePrayerVarKey()

        val magicOn = runtime.applyFightCaveActionAndTick(player, HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMagic))
        assertTrue(magicOn.actionApplied)
        assertTrue(player.containsVarbit(key, "protect_from_magic"))
        assertFalse(player.containsVarbit(key, "protect_from_melee"))
        assertFalse(player.containsVarbit(key, "protect_from_missiles"))

        val meleeOn = runtime.applyFightCaveActionAndTick(player, HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMelee))
        assertTrue(meleeOn.actionApplied)
        assertFalse(player.containsVarbit(key, "protect_from_magic"))
        assertTrue(player.containsVarbit(key, "protect_from_melee"))
        assertFalse(player.containsVarbit(key, "protect_from_missiles"))

        val meleeOff = runtime.applyFightCaveActionAndTick(player, HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMelee))
        assertTrue(meleeOff.actionApplied)
        assertFalse(player.containsVarbit(key, "protect_from_magic"))
        assertFalse(player.containsVarbit(key, "protect_from_melee"))
        assertFalse(player.containsVarbit(key, "protect_from_missiles"))
    }
}
