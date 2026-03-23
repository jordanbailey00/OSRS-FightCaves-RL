package content.area.karamja.tzhaar_city

import WorldTest
import content.quest.instance
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.type.Tile
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class FightCaveBackendActionAdapterTest : WorldTest() {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var fightCave: TzhaarFightCave
    private lateinit var initializer: FightCaveEpisodeInitializer
    private lateinit var adapter: FightCaveBackendActionAdapter
    private lateinit var backendControl: FightCaveDemoBackendControl

    @BeforeEach
    fun setUpBackendControl() {
        fightCave = scripts.filterIsInstance<TzhaarFightCave>().single()
        initializer = FightCaveEpisodeInitializer(fightCave)
        adapter = FightCaveBackendActionAdapter()
        backendControl = FightCaveDemoBackendControl(adapter)
        Settings.load(
            mapOf(
                "fightCave.demo.backendControl.enabled" to "true",
                "fightCave.demo.backendControl.root" to tempDir.resolve("backend-control").toString(),
                "fightCave.demo.artifacts.path" to tempDir.resolve("artifacts").toString(),
            ),
        )
    }

    @Test
    fun `visible targets stay indexable and attack action uses visible target ordering`() {
        val player = createDemoPlayer("fight-cave-backend-attack")

        val first = adapter.visibleNpcTargets(player)
        val second = adapter.visibleNpcTargets(player)
        assertTrue(first.isNotEmpty(), "Expected visible targets after demo episode reset.")
        assertEquals(first, second, "Repeated target resolution without ticking should stay stable.")

        val result = adapter.apply(player, FightCaveBackendAction.AttackVisibleNpc(0))
        assertTrue(result.actionApplied)
        assertEquals("0", result.metadata["visible_npc_index"])
        assertEquals(first.first().npcIndex.toString(), result.metadata["target_npc_index"])
    }

    @Test
    fun `consumables and prayer toggles apply through headed backend action adapter`() {
        val player = createDemoPlayer("fight-cave-backend-consume")

        val prayerResult =
            adapter.apply(
                player,
                FightCaveBackendAction.ToggleProtectionPrayer(FightCaveBackendProtectionPrayer.ProtectFromMissiles),
            )
        assertTrue(prayerResult.actionApplied)
        assertEquals("true", prayerResult.metadata["active"])
        tick()

        player.levels.set(Skill.Constitution, 500)
        val sharksBefore = player.inventory.count("shark")
        val eatResult = adapter.apply(player, FightCaveBackendAction.EatShark)
        assertTrue(eatResult.actionApplied)
        assertEquals(sharksBefore - 1, player.inventory.count("shark"))
        tick(3)

        player.levels.set(Skill.Prayer, 10)
        val prayerPotion4Before = player.inventory.count("prayer_potion_4")
        val drinkResult = adapter.apply(player, FightCaveBackendAction.DrinkPrayerPotion)
        assertTrue(drinkResult.actionApplied)
        assertEquals(prayerPotion4Before - 1, player.inventory.count("prayer_potion_4"))
    }

    @Test
    fun `demo backend control processes request files and writes result artifacts`() {
        val player = createDemoPlayer("fight-cave-backend-stage")
        val inbox = tempDir.resolve("backend-control/inbox").toFile().apply { mkdirs() }
        val request = inbox.resolve("request-001.properties")
        request.writeText(
            """
            request_id=request-001
            account=${player.accountName}
            action_id=0
            submitted_at_ms=12345
            source=replay
            replay_id=test-replay
            step_index=4
            """.trimIndent(),
        )

        backendControl.run()

        val result = tempDir.resolve("backend-control/results/request-001.json").toFile()
        assertTrue(result.exists(), "Expected backend control result artifact.")
        val json = result.readText()
        assertTrue("\"action_name\":\"wait\"" in json)
        assertTrue("\"request_id\":\"request-001\"" in json)
        assertTrue("\"source\":\"replay\"" in json)
        assertTrue("\"replay_id\":\"test-replay\"" in json)
        assertTrue("\"step_index\":\"4\"" in json)
        assertTrue("\"visible_targets_before\"" in json)
        assertTrue("\"observation_before\"" in json)
        assertTrue("\"observation_after\"" in json)
        assertTrue("\"schema_id\":\"headless_observation_v1\"" in json)
    }

    private fun createDemoPlayer(name: String): Player {
        val player = createPlayer(Tile(2438, 5168), name)
        player["fight_cave_demo_profile"] = true
        initializer.reset(player, FightCaveEpisodeConfig(seed = 123L, startWave = 1))
        tick(8)
        assertTrue(player["fight_cave_demo_episode", false])
        assertNotNull(player.instance())
        return player
    }
}
