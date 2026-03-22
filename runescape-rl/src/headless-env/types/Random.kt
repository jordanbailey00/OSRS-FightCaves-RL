package sim.types

import kotlin.random.Random

data class RandomDiagnostics(
    val trackingEnabled: Boolean,
    val callCount: Long,
    val seed: Long?,
)

private val randomNextBitsMethod =
    Random::class.java
        .getDeclaredMethod("nextBits", Int::class.javaPrimitiveType)
        .apply { isAccessible = true }

private class TrackedRandom(
    private val delegate: Random,
    private val seed: Long?,
) : Random() {
    private var callCount: Long = 0

    override fun nextBits(bitCount: Int): Int {
        callCount++
        return try {
            randomNextBitsMethod.invoke(delegate, bitCount) as Int
        } catch (_: Throwable) {
            // Fallback keeps deterministic behavior even if reflective access changes.
            delegate.nextInt().ushr(32 - bitCount)
        }
    }

    fun diagnostics(): RandomDiagnostics = RandomDiagnostics(trackingEnabled = true, callCount = callCount, seed = seed)
}

var random: Random = Random
    private set

fun setRandom(rand: Random) {
    random = rand
}

fun setSeededRandom(seed: Long, trackCalls: Boolean = true) {
    val seeded = Random(seed)
    random = if (trackCalls) TrackedRandom(seeded, seed) else seeded
}

fun randomDiagnostics(): RandomDiagnostics {
    val tracked = random as? TrackedRandom
    return tracked?.diagnostics() ?: RandomDiagnostics(trackingEnabled = false, callCount = 0, seed = null)
}

fun randomCallCount(): Long = (random as? TrackedRandom)?.diagnostics()?.callCount ?: 0
