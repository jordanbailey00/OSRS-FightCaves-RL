package sim.engine.entity.character.npc

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import sim.cache.definition.data.ObjectDefinition
import sim.engine.Caller
import sim.engine.Script
import sim.engine.ScriptTest
import sim.engine.data.definition.ObjectDefinitions
import sim.engine.entity.character.npc.hunt.Hunt
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.floor.FloorItem
import sim.engine.entity.obj.GameObject
import sim.engine.script.KoinMock
import sim.types.Tile

class HuntTest {

    @Nested
    inner class HuntFloorItemTest : ScriptTest {
        override val checks = listOf(
            listOf("mode"),
        )
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            huntFloorItem(args[0]) {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Hunt.hunt(NPC("npc"), FloorItem(Tile.EMPTY, "item"), "mode")
        }

        override val apis = listOf(Hunt)

    }

    @Nested
    inner class HuntNPCTest : ScriptTest {
        override val checks = listOf(
            listOf("mode"),
        )
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            huntNPC(args[0]) {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Hunt.hunt(NPC("npc"), NPC("target"), "mode")
        }

        override val apis = listOf(Hunt)

    }

    @Nested
    inner class HuntPlayerTest : ScriptTest {
        override val checks = listOf(
            listOf("npc", "mode"),
            listOf("*", "mode"),
        )
        override val failedChecks = listOf(
            listOf("npc", "*"),
        )

        override fun Script.register(args: List<String>, caller: Caller) {
            huntPlayer(args[0], args[1]) {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Hunt.hunt(NPC("npc"), Player(), "mode")
        }

        override val apis = listOf(Hunt)

    }

    @Nested
    inner class HuntObjectTest : KoinMock(), ScriptTest {
        override val checks = listOf(
            listOf("mode"),
        )
        override val failedChecks = emptyList<List<String>>()

        @BeforeEach
        fun setup() {
            ObjectDefinitions.init(arrayOf(ObjectDefinition(0, stringId = "obj")))
        }

        override fun Script.register(args: List<String>, caller: Caller) {
            huntObject(args[0]) {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Hunt.hunt(NPC("npc"), GameObject(0), "mode")
        }

        override val apis = listOf(Hunt)

    }

}