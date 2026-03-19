data class DemoLiteSessionEvent(
    val eventType: String,
    val timestampMillis: Long,
    val sessionId: String,
    val episodeId: String?,
    val tick: Int?,
    val payload: LinkedHashMap<String, Any?>,
)
