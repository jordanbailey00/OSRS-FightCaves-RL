package sim.engine.entity.character

import org.junit.jupiter.api.Nested
import sim.engine.Caller
import sim.engine.Script
import sim.engine.ScriptTest
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player

class DeathTest {

    @Nested
    inner class PlayerDeathTest : ScriptTest {
        override val checks = listOf(
            listOf<String>(),
        )
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            playerDeath {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Death.killed(Player())
        }

        override val apis = listOf(Death)

    }

    @Nested
    inner class NPCDeathTest : ScriptTest {
        override val checks = listOf(
            listOf("npc"),
            listOf("*"),
        )
        override val failedChecks = emptyList<List<String>>()

        override fun Script.register(args: List<String>, caller: Caller) {
            npcDeath(args[0]) {
                caller.call()
            }
        }

        override fun invoke(args: List<String>) {
            Death.killed(NPC("npc"))
        }

        override val apis = listOf(Death)

    }

}