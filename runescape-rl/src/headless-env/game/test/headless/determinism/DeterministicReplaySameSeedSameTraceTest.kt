import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DeterministicReplaySameSeedSameTraceTest {

    @Test
    fun `deterministic replay is identical for same seed and action trace`() {
        val seed = 424242L
        val trace = deterministicFightCaveTrace()

        val first = executeHeadlessReplay(seed, trace, "determinism-same")
        val second = executeHeadlessReplay(seed, trace, "determinism-same")

        assertEquals(first.snapshots.size, second.snapshots.size)
        assertEquals(first.rngDiagnostics.trackingEnabled, second.rngDiagnostics.trackingEnabled)
        assertEquals(first.rngDiagnostics.seed, second.rngDiagnostics.seed)

        val firstObservations = first.snapshots.map { it.observation.toOrderedMap() }
        val secondObservations = second.snapshots.map { it.observation.toOrderedMap() }
        assertEquals(firstObservations, secondObservations)

        assertEquals(first.snapshots.map { it.actionResult?.actionApplied }, second.snapshots.map { it.actionResult?.actionApplied })
        assertEquals(first.snapshots.map { it.actionResult?.rejectionReason }, second.snapshots.map { it.actionResult?.rejectionReason })

        assertTrue(first.rngDiagnostics.trackingEnabled)
        assertTrue(first.rngDiagnostics.callCount > 0)
        assertTrue(second.rngDiagnostics.callCount > 0)
    }
}
