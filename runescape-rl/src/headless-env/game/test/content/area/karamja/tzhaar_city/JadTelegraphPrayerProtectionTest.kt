import content.area.karamja.tzhaar_city.JAD_HIT_TARGET_QUEUE_TICKS
import content.area.karamja.tzhaar_city.JAD_HIT_RESOLVE_OFFSET_TICKS
import content.area.karamja.tzhaar_city.jadTelegraphTraceOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class JadTelegraphPrayerProtectionTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `magic telegraph with protect magic active at resolution is fully protected`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("jad-prayer-magic-protected")
        runtime.resetFightCaveEpisode(player, seed = 701L, startWave = 1)

        val before = playerHitpoints(player)
        spawnJadWithTelegraphedAttack(player, attackId = "magic")

        val result = runtime.applyFightCaveAction(player, HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMagic))
        assertTrue(result.actionApplied)

        runtime.tick(JAD_HIT_RESOLVE_OFFSET_TICKS)
        assertEquals(before, playerHitpoints(player))
        assertEquals(100, player["protected_damage", 0])
    }

    @Test
    fun `magic telegraph with no prayer at resolution deals damage`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("jad-prayer-magic-unprotected")
        runtime.resetFightCaveEpisode(player, seed = 702L, startWave = 1)

        val before = playerHitpoints(player)
        spawnJadWithTelegraphedAttack(player, attackId = "magic")

        runtime.tick(JAD_HIT_RESOLVE_OFFSET_TICKS)
        assertTrue(playerHitpoints(player) < before)
        assertEquals(0, player["protected_damage", 0])
    }

    @Test
    fun `ranged telegraph with protect missiles active at resolution is fully protected`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("jad-prayer-range-protected")
        runtime.resetFightCaveEpisode(player, seed = 703L, startWave = 1)

        val before = playerHitpoints(player)
        spawnJadWithTelegraphedAttack(player, attackId = "range")

        val result = runtime.applyFightCaveAction(player, HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMissiles))
        assertTrue(result.actionApplied)

        runtime.tick(JAD_HIT_RESOLVE_OFFSET_TICKS)
        assertEquals(before, playerHitpoints(player))
        assertEquals(100, player["protected_damage", 0])
    }

    @Test
    fun `ranged telegraph with wrong prayer at resolution deals damage`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("jad-prayer-range-wrong")
        runtime.resetFightCaveEpisode(player, seed = 704L, startWave = 1)

        val before = playerHitpoints(player)
        spawnJadWithTelegraphedAttack(player, attackId = "range")

        val result = runtime.applyFightCaveAction(player, HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMagic))
        assertTrue(result.actionApplied)

        runtime.tick(JAD_HIT_RESOLVE_OFFSET_TICKS)
        assertTrue(playerHitpoints(player) < before)
        assertEquals(0, player["protected_damage", 0])
    }

    @Test
    fun `prayer sampled before queued hit construction remains protective even if toggled off before visual resolve`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("jad-prayer-sampled-before-commit")
        runtime.resetFightCaveEpisode(player, seed = 705L, startWave = 1)

        val turnOn = runtime.applyFightCaveAction(player, HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMagic))
        assertTrue(turnOn.actionApplied)
        val beforeProtected = playerHitpoints(player)
        val jad = spawnJadWithTelegraphedAttack(player, attackId = "magic")

        runtime.tick(JAD_HIT_TARGET_QUEUE_TICKS)
        val turnOff = runtime.applyFightCaveAction(player, HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMagic))
        assertTrue(turnOff.actionApplied)
        runtime.tick(JAD_HIT_RESOLVE_OFFSET_TICKS - JAD_HIT_TARGET_QUEUE_TICKS)

        val trace = assertNotNull(jad.jadTelegraphTraceOrNull())
        assertEquals(beforeProtected, playerHitpoints(player))
        assertEquals(trace.telegraphStartTick + JAD_HIT_TARGET_QUEUE_TICKS, trace.prayerCheckTick)
        assertEquals("protect_from_magic", trace.sampledProtectionPrayer)
        assertEquals(true, trace.protectedAtPrayerCheck)
        assertEquals(0, trace.resolvedDamage)
    }

    @Test
    fun `activating prayer after queued hit construction is too late even before visual resolve`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("jad-prayer-too-late-after-commit")
        runtime.resetFightCaveEpisode(player, seed = 706L, startWave = 1)

        val before = playerHitpoints(player)
        val jad = spawnJadWithTelegraphedAttack(player, attackId = "range")

        runtime.tick(JAD_HIT_TARGET_QUEUE_TICKS)
        val turnOnLate = runtime.applyFightCaveAction(player, HeadlessAction.ToggleProtectionPrayer(HeadlessProtectionPrayer.ProtectFromMissiles))
        assertTrue(turnOnLate.actionApplied)
        runtime.tick(JAD_HIT_RESOLVE_OFFSET_TICKS - JAD_HIT_TARGET_QUEUE_TICKS)

        val trace = assertNotNull(jad.jadTelegraphTraceOrNull())
        assertTrue(playerHitpoints(player) < before)
        assertEquals(trace.telegraphStartTick + JAD_HIT_TARGET_QUEUE_TICKS, trace.prayerCheckTick)
        assertEquals("none", trace.sampledProtectionPrayer)
        assertEquals(false, trace.protectedAtPrayerCheck)
        assertEquals(100, trace.resolvedDamage)
    }
}
