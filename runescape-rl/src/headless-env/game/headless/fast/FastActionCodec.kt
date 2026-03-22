package headless.fast

enum class FastRejectionCode(val code: Int) {
    None(0),
    AlreadyActedThisTick(1),
    InvalidTargetIndex(2),
    TargetNotVisible(3),
    PlayerBusy(4),
    MissingConsumable(5),
    ConsumptionLocked(6),
    PrayerPointsDepleted(7),
    InsufficientRunEnergy(8),
    NoMovementRequired(9),
    UnsupportedActionId(10),
    InvalidPrayerId(11),
}

data class FastDecodedAction(
    val actionId: Int,
    val actionName: String,
    val tile: FastTile? = null,
    val visibleNpcIndex: Int? = null,
    val prayer: FastProtectionPrayer? = null,
    val rejectionCode: FastRejectionCode = FastRejectionCode.None,
)

object FastActionCodec {
    const val PACKED_WORD_COUNT: Int = 4

    fun decode(packedActions: IntArray, offset: Int): FastDecodedAction {
        require(offset >= 0) { "Packed action offset must be >= 0, got $offset." }
        require(offset + PACKED_WORD_COUNT <= packedActions.size) {
            "Packed action decode requires ${PACKED_WORD_COUNT} words at offset $offset, " +
                "got size=${packedActions.size}."
        }
        val actionId = packedActions[offset]
        val arg0 = packedActions[offset + 1]
        val arg1 = packedActions[offset + 2]
        val arg2 = packedActions[offset + 3]

        return when (actionId) {
            0 -> FastDecodedAction(actionId = 0, actionName = "wait")
            1 ->
                FastDecodedAction(
                    actionId = 1,
                    actionName = "walk_to_tile",
                    tile = FastTile(x = arg0, y = arg1, level = arg2),
                )
            2 ->
                FastDecodedAction(
                    actionId = 2,
                    actionName = "attack_visible_npc",
                    visibleNpcIndex = arg0,
                )
            3 -> decodePrayerAction(actionId, arg0)
            4 -> FastDecodedAction(actionId = 4, actionName = "eat_shark")
            5 -> FastDecodedAction(actionId = 5, actionName = "drink_prayer_potion")
            6 -> FastDecodedAction(actionId = 6, actionName = "toggle_run")
            else ->
                FastDecodedAction(
                    actionId = actionId,
                    actionName = "unknown_action",
                    rejectionCode = FastRejectionCode.UnsupportedActionId,
                )
        }
    }

    fun encode(
        actionId: Int,
        arg0: Int = 0,
        arg1: Int = 0,
        arg2: Int = 0,
    ): IntArray =
        intArrayOf(actionId, arg0, arg1, arg2)

    fun decodeBatch(packedActions: IntArray, envCount: Int): List<FastDecodedAction> {
        require(envCount >= 0) { "envCount must be >= 0, got $envCount." }
        require(packedActions.size == envCount * PACKED_WORD_COUNT) {
            "Packed action batch length mismatch: expected ${envCount * PACKED_WORD_COUNT}, " +
                "got ${packedActions.size}."
        }
        return List(envCount) { envIndex ->
            decode(packedActions, envIndex * PACKED_WORD_COUNT)
        }
    }

    private fun decodePrayerAction(actionId: Int, prayerIndex: Int): FastDecodedAction {
        val prayer =
            when (prayerIndex) {
                0 -> FastProtectionPrayer.ProtectFromMagic
                1 -> FastProtectionPrayer.ProtectFromMissiles
                2 -> FastProtectionPrayer.ProtectFromMelee
                else -> null
            }
        return if (prayer == null) {
            FastDecodedAction(
                actionId = actionId,
                actionName = "toggle_protection_prayer",
                rejectionCode = FastRejectionCode.InvalidPrayerId,
            )
        } else {
            FastDecodedAction(
                actionId = actionId,
                actionName = "toggle_protection_prayer",
                prayer = prayer,
            )
        }
    }
}
