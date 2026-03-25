package sim.engine.entity.character.mode.move

import org.junit.jupiter.api.Nested
import sim.engine.Caller
import sim.engine.Script
import sim.engine.ScriptTest
import sim.engine.data.definition.AreaDefinition
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.types.Tile
import sim.types.area.Rectangle
import kotlin.test.assertEquals

class MovedTest {

    @Nested
    inner class MovedTest : ScriptTest {
        override val checks = listOf(
            listOf<String>()
        )
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            moved { from ->
                caller.call()
                assertEquals(Tile(1, 2), from)
            }
        }

        override fun invoke(args: List<String>) {
            Moved.player(Player(), Tile(1, 2))
        }

        override val apis = listOf(Moved)

    }

    @Nested
    inner class NPCMovedTest : ScriptTest {
        override val checks = listOf(
            listOf("npc"),
            listOf("*"),
        )
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            npcMoved(args[0]) { from ->
                caller.call()
                assertEquals(Tile(1, 2), from)
            }
        }

        override fun invoke(args: List<String>) {
            Moved.npc(NPC("npc"), Tile(1, 2))
        }

        override val apis = listOf(Moved)
    }

    @Nested
    inner class EnteredTest : ScriptTest {
        override val checks = listOf(
            listOf("area"),
            listOf("*"),
        )
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            entered(args[0]) { area ->
                caller.call()
                assertEquals(Rectangle(Tile(1, 2), 1, 2), area.area)
            }
        }

        override fun invoke(args: List<String>) {
            val def = AreaDefinition("", area = Rectangle(Tile(1, 2), 1, 2), tags = emptySet())
            Moved.enter(Player(), args[0], def)
        }

        override val apis = listOf(Moved)
    }

    @Nested
    inner class ExitedTest : ScriptTest {
        override val checks = listOf(
            listOf("area"),
            listOf("*"),
        )
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            exited(args[0]) { area ->
                caller.call()
                assertEquals(Rectangle(Tile(1, 2), 1, 2), area.area)
            }
        }

        override fun invoke(args: List<String>) {
            val def = AreaDefinition("", area = Rectangle(Tile(1, 2), 1, 2), tags = emptySet())
            Moved.exit(Player(), args[0], def)
        }

        override val apis = listOf(Moved)
    }

}