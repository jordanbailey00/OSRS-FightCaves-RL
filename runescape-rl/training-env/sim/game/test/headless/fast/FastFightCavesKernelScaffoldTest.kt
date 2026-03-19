import content.area.karamja.tzhaar_city.TzhaarFightCave
import headless.fast.FIGHT_CAVES_FAST_KERNEL_SURFACE_CONTRACT_ID
import headless.fast.FastEpisodeConfig
import headless.fast.FastFightCavesKernel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class FastFightCavesKernelScaffoldTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `fast kernel scaffold exposes independent surface contract and shared action semantics`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val fightCave = runtime.loadedScripts.filterIsInstance<TzhaarFightCave>().single()

        val kernel = FastFightCavesKernel.scaffold(fightCave, runtime.configFiles)

        assertEquals(FIGHT_CAVES_FAST_KERNEL_SURFACE_CONTRACT_ID, kernel.contract.contractId)
        assertEquals(1, kernel.contract.version)
        assertEquals("headless_action_v1", kernel.contract.sharedActionSchemaId)
        assertEquals(1, kernel.contract.sharedActionSchemaVersion)
        assertEquals("headless_training_flat_observation_v1", kernel.contract.sharedFlatObservationSchemaId)
        assertEquals(1, kernel.contract.sharedFlatObservationSchemaVersion)
        assertEquals("fight_caves_mechanics_parity_trace_v1", kernel.contract.sharedParityTraceSchemaId)
        assertEquals(1, kernel.contract.sharedParityTraceSchemaVersion)
        val descriptor = kernel.describe()
        assertEquals("headless_training_flat_observation_v1", descriptor.flatObservationSchemaId)
        assertEquals(134, descriptor.flatObservationFeatureCount)
        assertEquals("fight_caves_v2_reward_features_v1", descriptor.rewardFeatureSchemaId)
        assertEquals(16, descriptor.rewardFeatureCount)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), kernel.actionSchema.descriptors.map { it.actionId })
        assertEquals(63, kernel.waveRegistry.waveCount)
        assertEquals(15, kernel.waveRegistry.definition(1).rotations.size)
    }

    @Test
    fun `fast kernel scaffold initializes deterministic slot template without step logic`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val fightCave = runtime.loadedScripts.filterIsInstance<TzhaarFightCave>().single()
        val kernel = FastFightCavesKernel.scaffold(fightCave, runtime.configFiles)

        val slot = kernel.initializeScaffoldSlot(slotIndex = 0, config = FastEpisodeConfig(seed = 111L, startWave = 1))

        assertEquals(0, slot.slotIndex)
        assertEquals(111L, slot.episodeSeed)
        assertEquals(0, slot.tick)
        assertEquals(700, slot.player.hitpointsCurrent)
        assertEquals(43, slot.player.prayerCurrent)
        assertEquals(1000, slot.player.inventory.ammo)
        assertEquals(20, slot.player.inventory.sharks)
        assertEquals(32, slot.player.inventory.prayerPotionDoses)
        assertEquals(1, slot.wave.currentWave)
        assertNull(slot.wave.resolvedRotation)
        assertTrue(slot.wave.remainingNpcCount > 0)
        assertTrue(slot.wave.spawnDirections.isEmpty())
        assertTrue(slot.visibleNpcs.isEmpty())
    }
}
