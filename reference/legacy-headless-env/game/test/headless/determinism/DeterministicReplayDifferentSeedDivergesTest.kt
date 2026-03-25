import kotlin.random.Random
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class DeterministicReplayDifferentSeedDivergesTest {

    @Test
    fun `deterministic replay diverges for different seeds with identical trace`() {
        val seedA = 1000L
        val seedB = findSeedWithDifferentOpeningRotation(seedA)
        val trace = deterministicFightCaveTrace()

        val first = executeHeadlessReplay(seedA, trace, "determinism-diff-a")
        val second = executeHeadlessReplay(seedB, trace, "determinism-diff-b")

        val firstFinal = first.snapshots.last().observation.toOrderedMap()
        val secondFinal = second.snapshots.last().observation.toOrderedMap()

        assertNotEquals(seedA, seedB)
        assertNotEquals(firstFinal, secondFinal)
        assertTrue(first.rngDiagnostics.callCount > 0)
        assertTrue(second.rngDiagnostics.callCount > 0)
    }

    private fun findSeedWithDifferentOpeningRotation(seed: Long): Long {
        val firstRotation = openingRotation(seed)
        var candidate = seed + 1
        while (openingRotation(candidate) == firstRotation) {
            candidate++
        }
        return candidate
    }

    private fun openingRotation(seed: Long): Int = (1..15).random(Random(seed))
}
