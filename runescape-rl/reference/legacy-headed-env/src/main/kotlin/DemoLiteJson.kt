object DemoLiteJson {
    fun encode(value: Any?): String = encode(value, pretty = false, depth = 0)

    fun encodePretty(value: Any?): String = encode(value, pretty = true, depth = 0)

    private fun encode(value: Any?, pretty: Boolean, depth: Int): String =
        when (value) {
            null -> "null"
            is String -> "\"${escape(value)}\""
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> encodeMap(value, pretty, depth)
            is Iterable<*> -> encodeIterable(value, pretty, depth)
            is Array<*> -> encodeIterable(value.asIterable(), pretty, depth)
            else -> "\"${escape(value.toString())}\""
        }

    private fun encodeMap(value: Map<*, *>, pretty: Boolean, depth: Int): String {
        if (value.isEmpty()) {
            return "{}"
        }
        val indent = if (pretty) "  ".repeat(depth + 1) else ""
        val closingIndent = if (pretty) "  ".repeat(depth) else ""
        val separator = if (pretty) ",\n" else ", "
        val entries =
            value.entries.joinToString(separator = separator) { (key, entryValue) ->
                val encodedKey = "\"${escape(key.toString())}\""
                val spacing = if (pretty) ": " else ":"
                val prefix = if (pretty) indent else ""
                prefix + encodedKey + spacing + encode(entryValue, pretty, depth + 1)
            }
        return if (pretty) {
            "{\n$entries\n$closingIndent}"
        } else {
            "{$entries}"
        }
    }

    private fun encodeIterable(value: Iterable<*>, pretty: Boolean, depth: Int): String {
        val values = value.toList()
        if (values.isEmpty()) {
            return "[]"
        }
        val indent = if (pretty) "  ".repeat(depth + 1) else ""
        val closingIndent = if (pretty) "  ".repeat(depth) else ""
        val separator = if (pretty) ",\n" else ", "
        val entries =
            values.joinToString(separator = separator) { entry ->
                val prefix = if (pretty) indent else ""
                prefix + encode(entry, pretty, depth + 1)
            }
        return if (pretty) {
            "[\n$entries\n$closingIndent]"
        } else {
            "[$entries]"
        }
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
