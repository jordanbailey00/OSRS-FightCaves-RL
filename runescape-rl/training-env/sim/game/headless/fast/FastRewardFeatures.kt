package headless.fast

const val FAST_REWARD_FEATURE_DAMAGE_DEALT_INDEX = 0
const val FAST_REWARD_FEATURE_DAMAGE_TAKEN_INDEX = 1
const val FAST_REWARD_FEATURE_NPC_KILL_INDEX = 2
const val FAST_REWARD_FEATURE_WAVE_CLEAR_INDEX = 3
const val FAST_REWARD_FEATURE_JAD_DAMAGE_DEALT_INDEX = 4
const val FAST_REWARD_FEATURE_JAD_KILL_INDEX = 5
const val FAST_REWARD_FEATURE_PLAYER_DEATH_INDEX = 6
const val FAST_REWARD_FEATURE_CAVE_COMPLETE_INDEX = 7
const val FAST_REWARD_FEATURE_FOOD_USED_INDEX = 8
const val FAST_REWARD_FEATURE_PRAYER_POTION_USED_INDEX = 9
const val FAST_REWARD_FEATURE_CORRECT_JAD_PRAYER_INDEX = 10
const val FAST_REWARD_FEATURE_WRONG_JAD_PRAYER_INDEX = 11
const val FAST_REWARD_FEATURE_INVALID_ACTION_INDEX = 12
const val FAST_REWARD_FEATURE_MOVEMENT_PROGRESS_INDEX = 13
const val FAST_REWARD_FEATURE_IDLE_PENALTY_INDEX = 14
const val FAST_REWARD_FEATURE_TICK_PENALTY_BASE_INDEX = 15

data class FastRewardSnapshot(
    val playerHitpointsCurrent: Int,
    val totalNpcHitpoints: Int,
    val aliveNpcCount: Int,
    val waveId: Int,
    val remainingNpcCount: Int,
    val jadHitpointsCurrent: Int,
    val jadAlive: Boolean,
    val sharks: Int,
    val prayerPotionDoses: Int,
)

data class FastJadResolveTrace(
    val committedAttackStyle: String,
    val protectedAtPrayerCheck: Boolean,
    val resolvedDamage: Int,
)

object FastRewardFeatureWriter {

    fun zeros(target: FloatArray? = null, envCount: Int = 1): FloatArray {
        val expectedSize = envCount * FIGHT_CAVES_FAST_REWARD_FEATURE_COUNT
        val destination =
            if (target == null) {
                FloatArray(expectedSize)
            } else {
                require(target.size == expectedSize) {
                    "Reward feature target size mismatch: expected $expectedSize, got ${target.size}."
                }
                target
            }
        destination.fill(0f)
        return destination
    }

    fun encodeTransition(
        before: FastRewardSnapshot,
        after: FastRewardSnapshot,
        actionName: String,
        actionAccepted: Boolean,
        terminal: FastTerminalState,
        jadResolve: FastJadResolveTrace?,
    ): FloatArray {
        val values = FloatArray(FIGHT_CAVES_FAST_REWARD_FEATURE_COUNT)
        values[FAST_REWARD_FEATURE_DAMAGE_DEALT_INDEX] =
            positiveDelta(before.totalNpcHitpoints, after.totalNpcHitpoints).toFloat()
        values[FAST_REWARD_FEATURE_DAMAGE_TAKEN_INDEX] =
            positiveDelta(before.playerHitpointsCurrent, after.playerHitpointsCurrent).toFloat()
        values[FAST_REWARD_FEATURE_NPC_KILL_INDEX] =
            positiveDelta(before.aliveNpcCount, after.aliveNpcCount).toFloat()
        values[FAST_REWARD_FEATURE_WAVE_CLEAR_INDEX] =
            if (before.remainingNpcCount > 0 && after.remainingNpcCount == 0) 1f else 0f
        values[FAST_REWARD_FEATURE_JAD_DAMAGE_DEALT_INDEX] =
            positiveDelta(before.jadHitpointsCurrent, after.jadHitpointsCurrent).toFloat()
        values[FAST_REWARD_FEATURE_JAD_KILL_INDEX] =
            if (before.jadAlive && !after.jadAlive) 1f else 0f
        values[FAST_REWARD_FEATURE_PLAYER_DEATH_INDEX] =
            if (terminal.terminalCode == FAST_TERMINAL_PLAYER_DEATH) 1f else 0f
        values[FAST_REWARD_FEATURE_CAVE_COMPLETE_INDEX] =
            if (terminal.terminalCode == FAST_TERMINAL_CAVE_COMPLETE) 1f else 0f
        values[FAST_REWARD_FEATURE_FOOD_USED_INDEX] =
            positiveDelta(before.sharks, after.sharks).toFloat()
        values[FAST_REWARD_FEATURE_PRAYER_POTION_USED_INDEX] =
            positiveDelta(before.prayerPotionDoses, after.prayerPotionDoses).toFloat()
        values[FAST_REWARD_FEATURE_INVALID_ACTION_INDEX] = if (actionAccepted) 0f else 1f
        values[FAST_REWARD_FEATURE_MOVEMENT_PROGRESS_INDEX] = 0f
        values[FAST_REWARD_FEATURE_IDLE_PENALTY_INDEX] = if (actionName == "wait") 1f else 0f
        values[FAST_REWARD_FEATURE_TICK_PENALTY_BASE_INDEX] = 1f

        if (jadResolve != null && jadResolve.resolvedDamage >= 0 && jadResolve.committedAttackStyle != "none") {
            if (jadResolve.protectedAtPrayerCheck) {
                values[FAST_REWARD_FEATURE_CORRECT_JAD_PRAYER_INDEX] = 1f
            } else {
                values[FAST_REWARD_FEATURE_WRONG_JAD_PRAYER_INDEX] = 1f
            }
        }
        return values
    }

    fun packRows(
        rows: List<FloatArray>,
        target: FloatArray? = null,
    ): FloatArray {
        val expectedSize = rows.size * FIGHT_CAVES_FAST_REWARD_FEATURE_COUNT
        val destination =
            if (target == null) {
                FloatArray(expectedSize)
            } else {
                require(target.size == expectedSize) {
                    "Reward feature target size mismatch: expected $expectedSize, got ${target.size}."
                }
                target
            }
        destination.fill(0f)
        rows.forEachIndexed { envIndex, row ->
            require(row.size == FIGHT_CAVES_FAST_REWARD_FEATURE_COUNT) {
                "Reward feature row size mismatch: expected $FIGHT_CAVES_FAST_REWARD_FEATURE_COUNT, got ${row.size}."
            }
            row.copyInto(destination, destinationOffset = envIndex * FIGHT_CAVES_FAST_REWARD_FEATURE_COUNT)
        }
        return destination
    }

    private fun positiveDelta(before: Int, after: Int): Int =
        (before - after).coerceAtLeast(0)
}
