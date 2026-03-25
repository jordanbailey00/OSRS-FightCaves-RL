package content.area.karamja.tzhaar_city

import WorldTest
import containsMessage
import content.entity.combat.dead
import content.entity.combat.underAttack
import content.entity.combat.target
import content.entity.combat.hit.directHit
import content.entity.player.effect.energy.MAX_RUN_ENERGY
import content.entity.player.effect.energy.runEnergy
import content.quest.instance
import content.quest.instanceOffset
import content.skill.prayer.PrayerConfigs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import world.gregs.voidps.engine.client.instruction.handle.interactNpc
import world.gregs.voidps.engine.client.instruction.handle.interactObject
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.Spawn
import world.gregs.voidps.engine.entity.character.mode.EmptyMode
import world.gregs.voidps.engine.entity.character.mode.combat.CombatMovement
import world.gregs.voidps.engine.entity.character.move.tele
import world.gregs.voidps.engine.entity.character.move.running
import world.gregs.voidps.engine.entity.character.npc.NPCs
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.equipment
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.network.login.protocol.visual.update.player.EquipSlot
import world.gregs.voidps.type.Tile
import java.io.File
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

internal class FightCaveEpisodeInitializerTest : WorldTest() {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var fightCave: TzhaarFightCave
    private lateinit var initializer: FightCaveEpisodeInitializer

    @BeforeEach
    fun setUpInitializer() {
        fightCave = scripts.filterIsInstance<TzhaarFightCave>().single()
        initializer = FightCaveEpisodeInitializer(fightCave)
        Settings.load(mapOf("fightCave.demo.artifacts.path" to tempDir.resolve("fight-caves-demo-artifacts").toString()))
    }

    @Test
    fun `episode init sets fixed stats resources and toggles`() {
        val player = createPlayer(Tile(2438, 5168), "fight-cave-episode-stats")

        player.levels.set(Skill.Attack, 99)
        player.levels.set(Skill.Strength, 99)
        player.levels.set(Skill.Defence, 1)
        player.levels.set(Skill.Constitution, 100)
        player.levels.set(Skill.Ranged, 1)
        player.levels.set(Skill.Prayer, 1)
        player.levels.set(Skill.Magic, 99)
        player.experience.removeBlock(Skill.Attack)
        player.experience.add(Skill.Attack, 5000.0)
        player.runEnergy = 250
        player.running = false
        player[PrayerConfigs.PRAYERS] = PrayerConfigs.BOOK_CURSES
        player[PrayerConfigs.USING_QUICK_PRAYERS] = true
        player[PrayerConfigs.SELECTING_QUICK_PRAYERS] = true
        player[PrayerConfigs.ACTIVE_PRAYERS] = setOf("protect_magic")
        player[PrayerConfigs.ACTIVE_CURSES] = setOf("leech_attack")
        player[PrayerConfigs.QUICK_PRAYERS] = setOf("protect_magic")
        player[PrayerConfigs.QUICK_CURSES] = setOf("leech_attack")
        player[PrayerConfigs.TEMP_QUICK_PRAYERS] = setOf("protect_magic")

        initializer.reset(player, FightCaveEpisodeConfig(seed = 42L, startWave = 1))

        val expected =
            mapOf(
                Skill.Attack to 1,
                Skill.Strength to 1,
                Skill.Defence to 70,
                Skill.Constitution to 700,
                Skill.Ranged to 70,
                Skill.Prayer to 43,
                Skill.Magic to 1,
            )

        for (skill in Skill.all) {
            val level = expected[skill] ?: 1
            assertEquals(level, player.levels.get(skill), "Current level mismatch for $skill")
            assertEquals(level, player.levels.getMax(skill), "Max level mismatch for $skill")
            assertTrue(player.experience.blocked(skill), "Expected XP to be blocked for $skill")
        }

        assertEquals(MAX_RUN_ENERGY, player.runEnergy)
        assertTrue(player.running)
        assertEquals("run", player["movement", "walk"])

        assertEquals(PrayerConfigs.BOOK_PRAYERS, player[PrayerConfigs.PRAYERS, PrayerConfigs.BOOK_PRAYERS])
        assertFalse(player[PrayerConfigs.USING_QUICK_PRAYERS, false])
        assertFalse(player[PrayerConfigs.SELECTING_QUICK_PRAYERS, false])
        assertFalse(player.contains(PrayerConfigs.USING_QUICK_PRAYERS))
        assertFalse(player.contains(PrayerConfigs.SELECTING_QUICK_PRAYERS))
        assertFalse(player.contains(PrayerConfigs.ACTIVE_PRAYERS))
        assertFalse(player.contains(PrayerConfigs.ACTIVE_CURSES))
        assertFalse(player.contains(PrayerConfigs.QUICK_PRAYERS))
        assertFalse(player.contains(PrayerConfigs.QUICK_CURSES))
        assertFalse(player.contains(PrayerConfigs.TEMP_QUICK_PRAYERS))
        assertFalse(player.contains("prayer_drain_counter"))
    }

    @Test
    fun `episode init sets fixed equipment and consumables`() {
        val player = createPlayer(Tile(2438, 5168), "fight-cave-episode-loadout")

        player.inventory.add("coins", 100_000)
        player.inventory.add("lobster", 5)
        player.equipment.set(EquipSlot.Hat.index, "iron_full_helm")
        player.equipment.set(EquipSlot.Weapon.index, "bronze_sword")
        player.equipment.set(EquipSlot.Ammo.index, "bronze_arrow", 123)

        initializer.reset(
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

    @Test
    fun `episode init resets wave variables and previous instance state`() {
        val player = createPlayer(Tile(2438, 5168), "fight-cave-episode-wave")

        initializer.reset(player, FightCaveEpisodeConfig(seed = 1337L, startWave = 6))
        val previousInstance = checkNotNull(player.instance())
        val injectedNpc = NPCs.add("tz_kih", player.tile)
        NPCs.run()
        assertTrue(injectedNpc.index != -1)

        player["fight_cave_wave"] = 99
        player["fight_cave_remaining"] = 0
        player["fight_caves_logout_warning"] = true

        initializer.reset(player, FightCaveEpisodeConfig(seed = 7331L, startWave = 1))
        val currentInstance = checkNotNull(player.instance())

        assertNotEquals(previousInstance.id, currentInstance.id)
        assertEquals(1, player["fight_cave_wave", -1])
        assertTrue(player["fight_cave_remaining", 0] > 0)
        assertFalse(player["fight_caves_logout_warning", true])
        assertTrue(player.contains("fight_cave_rotation"))
        assertTrue(player.contains("fight_cave_start_time"))

        for (level in 0..3) {
            assertEquals(0, NPCs.at(previousInstance.toLevel(level)).size, "Expected previous instance level $level to be cleared.")
        }
    }

    @Test
    fun `episode init uses provided seed for wave rotation`() {
        val player = createPlayer(Tile(2438, 5168), "fight-cave-episode-seed")

        val firstSeed = 101L
        val secondSeed = 202L

        val first = initializer.reset(player, FightCaveEpisodeConfig(seed = firstSeed, startWave = 1))
        val second = initializer.reset(player, FightCaveEpisodeConfig(seed = secondSeed, startWave = 1))
        val third = initializer.reset(player, FightCaveEpisodeConfig(seed = firstSeed, startWave = 1))

        assertEquals(firstSeed, first.seed)
        assertEquals(secondSeed, second.seed)
        assertEquals(firstSeed, third.seed)

        assertEquals(expectedRotation(firstSeed), first.rotation)
        assertEquals(expectedRotation(secondSeed), second.rotation)
        assertEquals(expectedRotation(firstSeed), third.rotation)
        assertEquals(firstSeed, player["episode_seed", -1L])
    }

    @Test
    fun `demo profile spawn boots directly into canonical fight cave episode`() {
        val player = createPlayer(Tile(3221, 3219), "fight-cave-demo-spawn")
        player["fight_cave_demo_profile"] = true
        player["fight_cave_demo_entry_pending"] = true
        player.levels.set(Skill.Attack, 99)
        player.inventory.add("coins", 1)

        Spawn.player(player)

        assertFalse(player["fight_cave_demo_entry_pending", true])
        assertTrue(player["fight_cave_demo_episode", false])
        assertEquals(1, player["fight_cave_wave", -1])
        assertNotNull(player.instance())
        assertEquals(fightCave.centre.add(player.instanceOffset()), player.tile)
        assertTrue(player.steps.isEmpty(), "Demo reset should not auto-path after entry.")
        assertEquals(PrayerConfigs.BOOK_PRAYERS, player[PrayerConfigs.PRAYERS, PrayerConfigs.BOOK_PRAYERS])
        assertEquals("coif", player.equipment[EquipSlot.Hat.index].id)
        assertEquals("rune_crossbow", player.equipment[EquipSlot.Weapon.index].id)
        assertFalse(player.queue.contains("welcome"), "Demo spawn should not queue the Lumbridge introduction.")
        assertTrue(player["task_disable_popups", false], "Demo spawn should suppress unrelated task popups.")
    }

    @Test
    fun `demo profile cave entry resets canonical state`() {
        val player = createPlayer(Tile(2438, 5168), "fight-cave-demo-enter")
        player["fight_cave_demo_profile"] = true
        player.inventory.add("coins", 1000)
        player.equipment.set(EquipSlot.Weapon.index, "bronze_sword")

        val entrance = GameObjects.find(Tile(2437, 5166), "cave_entrance_fight_cave")
        player.interactObject(entrance, "Enter")
        tick(3)

        assertEquals(1, player["fight_cave_wave", -1])
        assertTrue(player["fight_cave_demo_episode", false])
        assertNotNull(player.instance())
        assertEquals("rune_crossbow", player.equipment[EquipSlot.Weapon.index].id)
        assertEquals(8, player.inventory.count("prayer_potion_4"))
        assertEquals(20, player.inventory.count("shark"))
    }

    @Test
    fun `intro and jad warnings are suppressed only for demo episodes`() {
        val normalIntro = createPlayer(Tile(2438, 5168), "fight-cave-warning-normal-intro")
        fightCave.startWave(normalIntro, 1, start = true)
        assertTrue(normalIntro.queue.contains("fight_cave_warning"))

        val demoIntro = createPlayer(Tile(2438, 5168), "fight-cave-warning-demo-intro")
        demoIntro["fight_cave_demo_profile"] = true
        demoIntro["fight_cave_demo_episode"] = true
        fightCave.startWave(demoIntro, 1, start = true)
        assertFalse(demoIntro.queue.contains("fight_cave_warning"))

        val normalJad = createPlayer(Tile(2438, 5168), "fight-cave-warning-normal-jad")
        normalJad["fight_cave_rotation"] = 1
        fightCave.startWave(normalJad, 63, start = false)
        assertTrue(normalJad.queue.contains("fight_cave_warning"))

        val demoJad = createPlayer(Tile(2438, 5168), "fight-cave-warning-demo-jad")
        demoJad["fight_cave_demo_profile"] = true
        demoJad["fight_cave_demo_episode"] = true
        demoJad["fight_cave_rotation"] = 1
        fightCave.startWave(demoJad, 63, start = false)
        assertFalse(demoJad.queue.contains("fight_cave_warning"))
    }

    @Test
    fun `demo profile teleporting out of the cave resets the episode and blocks world access`() {
        val player = createPlayer(Tile(3221, 3219), "fight-cave-demo-teleport-out")
        player["fight_cave_demo_profile"] = true
        player["fight_cave_demo_entry_pending"] = true

        Spawn.player(player)
        val initialInstance = checkNotNull(player.instance()).id

        player.inventory.add("coins", 1000)
        player.tele(3221, 3219)
        tick(5)

        val resetInstance = checkNotNull(player.instance()).id
        assertNotEquals(initialInstance, resetInstance)
        assertEquals(1, player["fight_cave_wave", -1])
        assertTrue(player["fight_cave_demo_episode", false])
        assertTrue(player.containsMessage("Fight Caves demo resets if you leave the cave."))
        assertEquals(0, player.inventory.count("coins"))
        assertNotEquals(Tile(3221, 3219), player.tile)
        assertFalse(player.dead)
        assertTrue(NPCs.at(player.tile.regionLevel).isNotEmpty(), "Expected a fresh wave to spawn after world-access reset.")
        assertTrue(NPCs.at(player.tile.regionLevel).any { it.mode !is EmptyMode }, "Expected recycled NPCs to remain combat-interactable after world-access reset.")
        assertCanAttackRecycledWave(player)
    }

    @Test
    fun `demo profile leave recycles into a fresh fight cave episode`() {
        val player = createPlayer(Tile(3221, 3219), "fight-cave-demo-leave")
        player["fight_cave_demo_profile"] = true
        player["fight_cave_demo_entry_pending"] = true

        Spawn.player(player)
        val initialInstance = checkNotNull(player.instance()).id

        player.inventory.add("coins", 1000)
        with(fightCave) {
            player.leave(10)
        }
        tick(5)

        val resetInstance = checkNotNull(player.instance()).id
        assertNotEquals(initialInstance, resetInstance)
        assertEquals(1, player["fight_cave_wave", -1])
        assertTrue(player["fight_cave_demo_episode", false])
        assertEquals(0, player.inventory.count("coins"))
        assertNotEquals(fightCave.outside, player.tile)
        assertFalse(player.dead)
        assertTrue(NPCs.at(player.tile.regionLevel).isNotEmpty(), "Expected a fresh wave to spawn after leave recycle.")
        assertTrue(NPCs.at(player.tile.regionLevel).any { it.mode !is EmptyMode }, "Expected recycled NPCs to remain combat-interactable after leave recycle.")
        assertCanAttackRecycledWave(player)
    }

    @Test
    fun `demo profile death stays in the fight cave loop`() {
        val player = createPlayer(Tile(3221, 3219), "fight-cave-demo-death")
        player["fight_cave_demo_profile"] = true
        player["fight_cave_demo_entry_pending"] = true

        Spawn.player(player)
        val initialInstance = checkNotNull(player.instance()).id

        player.directHit(5_000)
        tick(12)

        val resetInstance = checkNotNull(player.instance()).id
        assertNotEquals(initialInstance, resetInstance)
        assertEquals(1, player["fight_cave_wave", -1])
        assertTrue(player["fight_cave_demo_episode", false])
        assertEquals(checkNotNull(player.instance()), player.tile.region)
        assertNotEquals(fightCave.outside, player.tile)
        assertFalse(player.dead)
        assertEquals(700, player.levels.get(Skill.Constitution))
        assertEquals(PrayerConfigs.BOOK_PRAYERS, player[PrayerConfigs.PRAYERS, PrayerConfigs.BOOK_PRAYERS])
        assertTrue(NPCs.at(player.tile.regionLevel).isNotEmpty(), "Expected a fresh wave to spawn NPCs after demo death reset.")
        assertTrue(NPCs.at(player.tile.regionLevel).any { it.mode !is EmptyMode }, "Expected post-death recycled NPCs to remain combat-interactable.")
        assertCanAttackRecycledWave(player)

        val sessionLog = File(player["fight_cave_demo_session_log_path", ""])
        val sessionLogText = sessionLog.readText()
        assertTrue(sessionLogText.contains("\"event\":\"death_reset_ready\""))
        assertTrue(sessionLogText.contains("\"death_queue_active\":false"))
        assertTrue(sessionLogText.contains("\"disconnected\":false"))
    }

    @Test
    fun `demo prayer interface forces the normal prayer book`() {
        val player = createPlayer(Tile(3221, 3219), "fight-cave-demo-prayer-book")
        player["fight_cave_demo_profile"] = true
        player["fight_cave_demo_entry_pending"] = true

        Spawn.player(player)
        player[PrayerConfigs.PRAYERS] = PrayerConfigs.BOOK_CURSES
        player[PrayerConfigs.ACTIVE_CURSES] = setOf("deflect_magic")
        player["prayer_drain_counter"] = 42
        player.softTimers.start("prayer_drain")

        player.open("prayer_list")
        tick(1)

        assertEquals(PrayerConfigs.BOOK_PRAYERS, player[PrayerConfigs.PRAYERS, PrayerConfigs.BOOK_PRAYERS])
        assertFalse(player.contains(PrayerConfigs.ACTIVE_CURSES))
        assertFalse(player.softTimers.contains("prayer_drain"))
        assertFalse(player.contains("prayer_drain_counter"))
    }

    @Test
    fun `demo observability writes manifest session log and checklist artifacts`() {
        val player = createPlayer(Tile(3221, 3219), "fight-cave-demo-artifacts")
        player["fight_cave_demo_profile"] = true
        player["fight_cave_demo_entry_pending"] = true

        Spawn.player(player)

        val sessionLog = File(player["fight_cave_demo_session_log_path", ""])
        val manifest = File(player["fight_cave_demo_last_manifest_path", ""])
        val checklist = File(player["fight_cave_demo_checklist_path", ""])

        tick(10)

        assertTrue(sessionLog.exists())
        assertTrue(manifest.exists())
        assertTrue(checklist.exists())

        val sessionLogText = sessionLog.readText()
        assertTrue(sessionLogText.contains("\"event\":\"session_started\""))
        assertTrue(sessionLogText.contains("\"event\":\"entry_requested\""))
        assertTrue(sessionLogText.contains("\"event\":\"starter_state_manifest_written\""))
        assertTrue(sessionLogText.contains("\"event\":\"episode_reset\""))
        assertTrue(sessionLogText.contains("\"event\":\"episode_reset_followup\""))

        val manifestText = manifest.readText()
        assertTrue(manifestText.contains("\"manifest_type\":\"fight_cave_demo_starter_state\""))
        assertTrue(manifestText.contains("\"source_contract\":\"RLspec.md\""))
        assertTrue(manifestText.contains("\"prayer_book\":\"normal\""))
        assertTrue(manifestText.contains("\"drain_timer_active\":false"))
        assertTrue(manifestText.contains("\"task_disable_popups\":true"))
        assertTrue(manifestText.contains("\"item_id\":\"rune_crossbow\""))
        assertTrue(manifestText.contains("\"item_id\":\"adamant_bolts\""))
        assertTrue(manifestText.contains("\"start_wave\":1"))
        assertTrue(manifestText.contains("\"spawned_npcs\""))

        val checklistText = checklist.readText()
        assertTrue(checklistText.contains("- [x] Session log created"))
        assertTrue(checklistText.contains("- [x] Demo entry logged"))
        assertTrue(checklistText.contains("- [x] Starter-state manifest written"))
    }

    @Test
    fun `demo profile suppresses task completion chatter`() {
        val player = createPlayer(Tile(3221, 3219), "fight-cave-demo-task-chatter")
        player["fight_cave_demo_profile"] = true
        player["fight_cave_demo_entry_pending"] = true

        Spawn.player(player)
        player["on_the_run_task"] = true

        assertFalse(player.containsMessage("You have completed the Task"))
        assertEquals(0, player["task_popup", 0])
    }

    @Test
    fun `demo observability logs leave requests and world access violations`() {
        val player = createPlayer(Tile(3221, 3219), "fight-cave-demo-logs")
        player["fight_cave_demo_profile"] = true
        player["fight_cave_demo_entry_pending"] = true

        Spawn.player(player)
        with(fightCave) {
            player.leave(10)
        }
        player.tele(3221, 3219)
        tick(2)

        val sessionLog = File(player["fight_cave_demo_session_log_path", ""])
        val checklist = File(player["fight_cave_demo_checklist_path", ""])

        val sessionLogText = sessionLog.readText()
        assertTrue(sessionLogText.contains("\"event\":\"leave_requested\""))
        assertTrue(sessionLogText.contains("\"cause\":\"leave\""))
        assertTrue(sessionLogText.contains("\"event\":\"world_access_violation\""))
        assertTrue(sessionLogText.contains("\"cause\":\"area_exit\""))

        val checklistText = checklist.readText()
        assertTrue(checklistText.contains("Leave events: 1"))
        assertTrue(checklistText.contains("World-access violations: 1"))
    }

    private fun expectedRotation(seed: Long): Int = Random(seed).nextInt(1, 16)

    private fun assertCanAttackRecycledWave(player: world.gregs.voidps.engine.entity.character.player.Player) {
        val npcs = NPCs.at(player.tile.regionLevel)
        val npc = npcs.minByOrNull { it.tile.distanceTo(player.tile) } ?: fail("Expected at least one NPC in the recycled Fight Caves wave.")
        repeat(8) {
            player.interactNpc(npc, "Attack")
            tick(1)
            if (player.mode is CombatMovement || player.target == npc || npc.target == player) {
                return
            }
        }
        val playerAttacker = player.get<Any>("attacker")
        fail(
            "Expected the player to be able to attack the recycled Fight Caves wave. " +
                "playerMode=${player.mode::class.simpleName} playerTarget=${player.target} playerUnderAttack=${player.underAttack} " +
                "playerAttacker=$playerAttacker npcMode=${npc.mode::class.simpleName} npcTarget=${npc.target} " +
                "npcUnderAttack=${npc.underAttack} npcIndex=${npc.index} npcTile=${npc.tile} playerTile=${player.tile}"
        )
    }
}
