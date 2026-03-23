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

        // Wave-transition fix: when the game atomically clears a wave and starts the next
        // within a single tick, the after snapshot reflects the NEW wave's state (more NPCs,
        // higher total HP, remaining > 0). The naive before/after delta misses the kill/clear.
        // Detect wave transitions by comparing waveId and attribute the old wave's kills.
        val waveTransitioned = after.waveId > before.waveId && before.waveId > 0

        if (waveTransitioned) {
            // The old wave was fully cleared. All of the before wave's alive NPCs were killed,
            // and all of their HP was dealt as damage. The after snapshot's NPCs belong to the
            // NEW wave and should not reduce the damage/kill credit.
            values[FAST_REWARD_FEATURE_DAMAGE_DEALT_INDEX] = before.totalNpcHitpoints.toFloat()
            values[FAST_REWARD_FEATURE_NPC_KILL_INDEX] = before.aliveNpcCount.toFloat()
            values[FAST_REWARD_FEATURE_WAVE_CLEAR_INDEX] = (after.waveId - before.waveId).toFloat()
        } else {
            values[FAST_REWARD_FEATURE_DAMAGE_DEALT_INDEX] =
                positiveDelta(before.totalNpcHitpoints, after.totalNpcHitpoints).toFloat()
            values[FAST_REWARD_FEATURE_NPC_KILL_INDEX] =
                positiveDelta(before.aliveNpcCount, after.aliveNpcCount).toFloat()
            values[FAST_REWARD_FEATURE_WAVE_CLEAR_INDEX] =
                if (before.remainingNpcCount > 0 && after.remainingNpcCount == 0) 1f else 0f
        }

        values[FAST_REWARD_FEATURE_DAMAGE_TAKEN_INDEX] =
            positiveDelta(before.playerHitpointsCurrent, after.playerHitpointsCurrent).toFloat()
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
        // Step 32: movement_progress fires when the agent issues a walk action.
        // Combined with a negative weight, this discourages wasteful movement
        // that moves the player away from auto-retaliation combat range.
        values[FAST_REWARD_FEATURE_MOVEMENT_PROGRESS_INDEX] = if (actionName == "walk_to_tile") 1f else 0f
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
