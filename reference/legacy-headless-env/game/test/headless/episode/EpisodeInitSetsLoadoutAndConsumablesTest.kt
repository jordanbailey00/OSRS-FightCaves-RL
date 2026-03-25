import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.engine.inv.add
import sim.engine.inv.equipment
import sim.engine.inv.inventory
import kotlin.test.assertEquals

internal class EpisodeInitSetsLoadoutAndConsumablesTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `episode init sets fixed equipment and consumables`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = true)
        val player = createHeadlessPlayer("headless-episode-loadout")

        player.inventory.add("coins", 100_000)
        player.inventory.add("lobster", 5)
        player.equipment.set(EquipSlot.Hat.index, "iron_full_helm")
        player.equipment.set(EquipSlot.Weapon.index, "bronze_sword")
        player.equipment.set(EquipSlot.Ammo.index, "bronze_arrow", 123)

        runtime.resetFightCaveEpisode(
            player,
            FightCaveEpisodeConfig(seed = 111L, startWave = 1, ammo = 1500, prayerPotions = 8, sharks = 20),
        )

        assertEquals("coif", player.equipment[EquipSlot.Hat.index].id)
        assertEquals("rune_crossbow", player.equipment[EquipSlot.Weapon.index].id)
        assertEquals("black_dragonhide_body", player.equipment[EquipSlot.Chest.index].id)
        assertEquals("black_dragonhide_chaps", player.equipment[EquipSlot.Legs.index].id)
        assertEquals("black_dragonhide_vambraces", player.equipment[EquipSlot.Hands.index].id)
        assertEquals("snakeskin_boots", player.equipment[EquipSlot.Feet.index].id)
        assertEquals("adamant_bolts", player.equipment[EquipSlot.Ammo.index].id)
        assertEquals(1500, player.equipment[EquipSlot.Ammo.index].amount)
        assertEquals(7, player.equipment.count)

        assertEquals(8, player.inventory.count("prayer_potion_4"))
        assertEquals(20, player.inventory.count("shark"))
        assertEquals(28, player.inventory.count)
    }
}
