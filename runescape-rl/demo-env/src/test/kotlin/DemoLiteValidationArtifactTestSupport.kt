import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class DemoLiteValidationCheck(
    val name: String,
    val passed: Boolean,
    val note: String,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "name" to name,
            "passed" to passed,
            "note" to note,
        )
}

object DemoLiteValidationArtifactTestSupport {
    val reportsDir: Path =
        Paths.get(
            System.getProperty("demoLiteReportsDir", "build/reports/demo-lite")
        )

    fun writeJsonArtifact(fileName: String, payload: Any?) {
        Files.createDirectories(reportsDir)
        Files.writeString(reportsDir.resolve(fileName), encodeJson(payload) + "\n")
    }

    fun writeChecklistArtifact(fileName: String, title: String, checks: List<DemoLiteValidationCheck>) {
        val payload =
            linkedMapOf(
                "title" to title,
                "all_passed" to checks.all { it.passed },
                "checks" to checks.map { it.toOrderedMap() },
        )
        writeJsonArtifact(fileName, payload)
    }

    fun writeTextArtifact(fileName: String, text: String) {
        Files.createDirectories(reportsDir)
        Files.writeString(reportsDir.resolve(fileName), text)
    }

    private fun encodeJson(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> "\"${escape(value)}\""
            is Number, is Boolean -> value.toString()
            is Map<*, *> ->
                value.entries.joinToString(prefix = "{", postfix = "}") { (key, entryValue) ->
                    val encodedKey = "\"${escape(key.toString())}\""
                    "$encodedKey:${encodeJson(entryValue)}"
                }
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { entry -> encodeJson(entry) }
            is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { entry -> encodeJson(entry) }
            else -> error("Unsupported JSON payload type: ${value::class.qualifiedName}")
        }

    private fun escape(value: String): String =
        buildString(value.length + 8) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
}

inline fun <T> withDemoLiteSession(block: (DemoLiteFightCavesSession) -> T): T {
    val runtime =
        OracleMain.bootstrap(
            loadContentScripts = true,
            startWorld = true,
            installShutdownHook = false,
            settingsOverrides = DemoLiteScopePolicy.runtimeSettingsOverrides,
        )
    val config = DemoLiteConfig()
    val session = DemoLiteFightCavesSession(runtime, DemoLiteEpisodeInitializer(runtime, config), config)
    return try {
        block(session)
    } finally {
        runtime.shutdown()
    }
}
