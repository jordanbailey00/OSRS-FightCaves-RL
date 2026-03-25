object DemoLiteJadTelegraph {
    fun currentStateCode(observation: HeadlessObservationV1): Int =
        observation.npcs.firstOrNull { it.id == "tztok_jad" }?.jadTelegraphState ?: 0

    fun describe(observation: HeadlessObservationV1): String =
        when (val state = currentStateCode(observation)) {
            0 -> "idle"
            1 -> "magic_windup"
            2 -> "ranged_windup"
            else -> "unknown($state)"
        }
}
