package headless.fast

import content.area.karamja.tzhaar_city.JAD_HIT_CLIENT_DELAY
import content.area.karamja.tzhaar_city.JAD_HIT_RESOLVE_OFFSET_TICKS
import content.area.karamja.tzhaar_city.JAD_HIT_TARGET_QUEUE_TICKS

enum class FastJadTelegraphState(val encoded: Int) {
    Idle(0),
    MagicWindup(1),
    RangedWindup(2),
}

enum class FastJadCommittedAttackStyle(
    val attackId: String,
    val maxHit: Int,
) {
    None("", 0),
    Magic("magic", 950),
    Ranged("range", 970),
}

data class FastJadTelegraphSpec(
    val hitTargetQueueTicks: Int,
    val hitClientDelayTicks: Int,
    val hitResolveOffsetTicks: Int,
    val styles: List<FastJadCommittedAttackStyle>,
)

val FAST_JAD_TELEGRAPH_SPEC =
    FastJadTelegraphSpec(
        hitTargetQueueTicks = JAD_HIT_TARGET_QUEUE_TICKS,
        hitClientDelayTicks = JAD_HIT_CLIENT_DELAY,
        hitResolveOffsetTicks = JAD_HIT_RESOLVE_OFFSET_TICKS,
        styles = listOf(FastJadCommittedAttackStyle.Magic, FastJadCommittedAttackStyle.Ranged),
    )
