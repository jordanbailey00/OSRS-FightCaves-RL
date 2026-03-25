package sim.engine.entity

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import sim.cache.definition.data.ObjectDefinition
import sim.engine.Caller
import sim.engine.Script
import sim.engine.ScriptTest
import sim.engine.data.ConfigFiles
import sim.engine.data.definition.ObjectDefinitions
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.floor.FloorItem
import sim.engine.entity.obj.GameObject
import sim.engine.script.KoinMock
import sim.types.Tile

class SpawnTest {

    @Nested
    inner class PlayerSpawnTest : ScriptTest {
        override val checks = emptyList<List<String>>()
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            playerSpawn {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Spawn.player(Player())
        }

        override val apis = listOf(Spawn)

    }

    @Nested
    inner class NPCSpawnTest : ScriptTest {
        override val checks = listOf(
            listOf("npc"),
            listOf("*"),
        )

        override val failedChecks = listOf(
            listOf("npc_2"),
        )

        override fun Script.register(args: List<String>, caller: Caller) {
            npcSpawn(args[0]) {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Spawn.npc(NPC("npc"))
        }

        override val apis = listOf(Spawn)

    }

    @Nested
    inner class ObjectSpawnTest : KoinMock(), ScriptTest {
        override val checks = listOf(
            listOf("obj"),
            listOf("*"),
        )

        override val failedChecks = listOf(
            listOf("obj_2"),
        )

        @BeforeEach
        fun setup() {
            ObjectDefinitions.init(arrayOf(ObjectDefinition(0, stringId = "obj")))
        }

        override fun Script.register(args: List<String>, caller: Caller) {
            objectSpawn(args[0]) {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Spawn.gameObject(GameObject(0))
        }

        override val apis = listOf(Spawn)

    }

    @Nested
    inner class FloorItemSpawnTest : ScriptTest {
        override val checks = listOf(
            listOf("floor_item"),
            listOf("*"),
        )

        override val failedChecks = listOf(
            listOf("floor_item_2"),
        )

        override fun Script.register(args: List<String>, caller: Caller) {
            floorItemSpawn(args[0]) {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Spawn.floorItem(FloorItem(Tile.EMPTY, "floor_item"))
        }

        override val apis = listOf(Spawn)

    }

    @Nested
    inner class WorldSpawnTest : ScriptTest {
        override val checks = emptyList<List<String>>()
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            worldSpawn {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Spawn.world(ConfigFiles(mapOf()))
        }

        override val apis = listOf(Spawn)

    }
}