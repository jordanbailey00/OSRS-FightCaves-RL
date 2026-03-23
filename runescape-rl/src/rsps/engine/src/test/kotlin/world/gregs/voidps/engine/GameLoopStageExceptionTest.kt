package world.gregs.voidps.engine

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class GameLoopStageExceptionTest {

    @Test
    fun `Stage exception does not stop later stages`() {
        var count = 0
        val loop = GameLoop(
            listOf(
                Runnable { count++ },
                Runnable { throw IllegalStateException("Test") },
                Runnable { count++ },
            ),
        )

        loop.tick()

        assertEquals(2, count)
    }
}
