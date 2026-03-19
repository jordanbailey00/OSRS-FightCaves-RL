package headless.manifest

import sim.config.ConfigReader
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal data class HeadlessManifest(
    val root: Path,
    private val sections: Map<String, Map<String, Any>>,
) {
    fun section(name: String): Map<String, Any> = sections[name] ?: error("Missing section [$name] in headless manifest.")

    fun string(section: String, key: String): String = section(section)[key] as? String ?: error("Missing string '$key' in section [$section].")

    fun int(section: String, key: String): Int = section(section)[key] as? Int ?: error("Missing int '$key' in section [$section].")

    @Suppress("UNCHECKED_CAST")
    fun strings(section: String, key: String): List<String> {
        val values = section(section)[key] as? List<*> ?: error("Missing list '$key' in section [$section].")
        return values.map {
            it as? String ?: error("Expected list '$key' in section [$section] to contain only strings.")
        }
    }

    fun fileExists(relativePath: String): Boolean = Files.isRegularFile(root.resolve(normalize(relativePath)))

    fun sourceExistsForClass(className: String): Boolean = fileExists("game/${className.replace('.', '/')}.kt")

    fun countFilesInGlobBase(glob: String): Int {
        val normalized = normalize(glob)
        require(normalized.endsWith("/**")) { "Unsupported glob format '$glob'. Expected trailing '/**'." }
        val base = root.resolve(normalized.removeSuffix("/**"))
        if (!Files.isDirectory(base)) {
            return 0
        }
        var count = 0
        Files.walk(base).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                if (Files.isRegularFile(iterator.next())) {
                    count++
                }
            }
        }
        return count
    }

    fun idExistsInData(id: String): Boolean {
        val pattern = Regex("""\b${Regex.escape(id)}\b""")
        return dataTomlContents.any { pattern.containsMatchIn(it) }
    }

    private val dataTomlContents: List<String> by lazy {
        val dataPath = root.resolve("data")
        if (!Files.isDirectory(dataPath)) {
            return@lazy emptyList()
        }
        val contents = mutableListOf<String>()
        Files.walk(dataPath).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (!Files.isRegularFile(path) || !path.toString().endsWith(".toml")) {
                    continue
                }
                contents += Files.readString(path)
            }
        }
        contents
    }

    companion object {
        private fun normalize(path: String): String = path.replace("\\", "/").trimStart('/')
    }
}

internal object HeadlessManifestLoader {
    fun load(): HeadlessManifest {
        val root = locateRepositoryRoot()
        val file = root.resolve("config/headless_manifest.toml")
        require(Files.isRegularFile(file)) { "Missing required manifest file: $file" }
        val sections =
            BufferedInputStream(Files.newInputStream(file)).use { stream ->
                ConfigReader(stream, 1024, file.toString()).use { reader ->
                    reader.sections(expectedSections = 16, expectedSize = 16)
                }
            }
        return HeadlessManifest(root, sections)
    }

    private fun locateRepositoryRoot(): Path {
        var current = Paths.get("").toAbsolutePath().normalize()
        while (true) {
            val manifest = current.resolve("config/headless_manifest.toml")
            val settings = current.resolve("settings.gradle.kts")
            val gameDir = current.resolve("game")
            if (Files.isRegularFile(manifest) && Files.isRegularFile(settings) && Files.isDirectory(gameDir)) {
                return current
            }
            current = current.parent ?: break
        }
        error("Unable to locate repository root from ${Paths.get("").toAbsolutePath().normalize()}.")
    }
}
