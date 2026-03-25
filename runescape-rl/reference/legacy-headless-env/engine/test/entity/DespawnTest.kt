package sim.engine.entity

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import sim.cache.definition.data.ObjectDefinition
import sim.engine.Caller
import sim.engine.Script
import sim.engine.ScriptTest
import sim.engine.data.definition.ObjectDefinitions
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.floor.FloorItem
import sim.engine.entity.obj.GameObject
import sim.engine.script.KoinMock
import sim.types.Tile

class DespawnTest {

    @Nested
    inner class PlayerDespawnTest : ScriptTest {
        override val checks = emptyList<List<String>>()
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            playerDespawn {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Despawn.player(Player())
        }

        override val apis = listOf(Despawn)

    }

    @Nested
    inner class NPCDespawnTest : ScriptTest {
        override val checks = listOf(
            listOf("npc"),
            listOf("*"),
        )

        override val failedChecks = listOf(
            listOf("npc_2"),
        )

        override fun Script.register(args: List<String>, caller: Caller) {
            npcDespawn(args[0]) {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Despawn.npc(NPC("npc"))
        }

        override val apis = listOf(Despawn)

    }

    @Nested
    inner class ObjectDespawnTest : KoinMock(), ScriptTest {
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
            objectDespawn(args[0]) {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Despawn.gameObject(GameObject(0))
        }

        override val apis = listOf(Despawn)

    }

    @Nested
    inner class FloorItemDespawnTest : ScriptTest {
        override val checks = listOf(
            listOf("floor_item"),
            listOf("*"),
        )

        override val failedChecks = listOf(
            listOf("floor_item_2"),
        )

        override fun Script.register(args: List<String>, caller: Caller) {
            floorItemDespawn(args[0]) {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Despawn.floorItem(FloorItem(Tile.EMPTY, "floor_item"))
        }

        override val apis = listOf(Despawn)

    }

    @Nested
    inner class WorldDespawnTest : ScriptTest {
        override val checks = emptyList<List<String>>()
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            worldDespawn {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Despawn.world()
        }

        override val apis = listOf(Despawn)

    }
}