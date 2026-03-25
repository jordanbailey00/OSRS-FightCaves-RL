import sim.config.ConfigReader
import sim.engine.data.ConfigFiles
import sim.engine.data.Settings
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

private const val HEADLESS_DATA_ALLOWLIST_PATH_KEY = "headless.data.allowlist.path"
private const val DEFAULT_HEADLESS_DATA_ALLOWLIST_PATH = "config/headless_data_allowlist.toml"

data class HeadlessDataAllowlist(
    val repositoryRoot: Path,
    val sourcePath: Path,
    val requiredFiles: List<String>,
    val optionalFiles: List<String>,
    val requiredSourceRegions: List<Int>,
    val requiredSettings: List<String>,
)

fun loadHeadlessDataAllowlist(path: String = Settings[HEADLESS_DATA_ALLOWLIST_PATH_KEY, DEFAULT_HEADLESS_DATA_ALLOWLIST_PATH]): HeadlessDataAllowlist {
    val root = locateRepositoryRoot()
    val source = resolvePath(root, path)
    require(source.isRegularFile()) { "Headless data allowlist file not found: $source" }

    val sections =
        BufferedInputStream(Files.newInputStream(source)).use { stream ->
            ConfigReader(stream, 4096, source.toString()).use { reader ->
                reader.sections(expectedSections = 8, expectedSize = 32)
            }
        }

    fun strings(section: String, key: String): List<String> {
        val map = sections[section] ?: return emptyList()
        val value = map[key] ?: return emptyList()
        val values = value as? List<*> ?: error("Expected [$section].$key to be a list.")
        return values.map {
            it as? String ?: error("Expected [$section].$key to contain only strings.")
        }
    }

    fun ints(section: String, key: String): List<Int> {
        val map = sections[section] ?: return emptyList()
        val value = map[key] ?: return emptyList()
        val values = value as? List<*> ?: error("Expected [$section].$key to be a list.")
        return values.map {
            when (it) {
                is Int -> it
                is Long -> it.toInt()
                else -> error("Expected [$section].$key to contain only integers.")
            }
        }
    }

    return HeadlessDataAllowlist(
        repositoryRoot = root,
        sourcePath = source,
        requiredFiles = strings("files", "required"),
        optionalFiles = strings("files", "optional"),
        requiredSourceRegions = ints("regions", "required_source_regions"),
        requiredSettings = strings("settings", "required"),
    )
}

fun resolveAllowlistedDataFiles(allowlist: HeadlessDataAllowlist): List<Path> {
    val resolvedRequired =
        allowlist.requiredFiles.map { relative ->
            val path = resolvePath(allowlist.repositoryRoot, relative)
            if (!Files.isRegularFile(path)) {
                throw NoSuchFileException("Required allowlisted file missing: '$relative' (resolved: '$path').")
            }
            path
        }

    val resolvedOptional =
        allowlist.optionalFiles.mapNotNull { relative ->
            val path = resolvePath(allowlist.repositoryRoot, relative)
            if (Files.isRegularFile(path)) {
                path
            } else {
                null
            }
        }

    return (resolvedRequired + resolvedOptional)
        .map { it.toAbsolutePath().normalize() }
        .distinct()
}

fun loadHeadlessConfigFiles(allowlist: HeadlessDataAllowlist = loadHeadlessDataAllowlist()): ConfigFiles {
    val files = resolveAllowlistedDataFiles(allowlist)
    validateRequiredSettings(allowlist.requiredSettings, files)

    if (allowlist.requiredSourceRegions.isNotEmpty() && Settings.getOrNull("headless.map.regions") == null) {
        Settings.load(
            mapOf(
                "headless.map.regions" to allowlist.requiredSourceRegions.joinToString(","),
            ),
        )
    }

    val map = mutableMapOf<String, MutableList<String>>()
    val extensions = mutableSetOf<String>()
    for (file in files) {
        val extension = file.name.substringAfter('.')
        map.getOrPut(extension) { mutableListOf() }.add(file.toString())
        extensions += extension
    }

    return ConfigFiles(map = map, cacheUpdate = true, extensions = extensions)
}

private fun validateRequiredSettings(requiredSettings: List<String>, files: List<Path>) {
    val normalized = files.map { it.toString().replace('\\', '/') }

    for (key in requiredSettings) {
        val expected = Settings.getOrNull(key) ?: throw IllegalStateException("Required setting '$key' is missing for headless data validation.")
        if (expected.isBlank()) {
            throw IllegalStateException("Required setting '$key' is blank for headless data validation.")
        }
        if (!expected.endsWith(".toml")) {
            continue
        }
        val found = normalized.any { absolute -> absolute.endsWith("/$expected") || absolute.endsWith(expected) }
        if (!found) {
            throw IllegalStateException("Required setting '$key' value '$expected' is not present in headless data allowlist.")
        }
    }
}

internal fun locateRepositoryRoot(): Path {
    var current = Paths.get("").toAbsolutePath().normalize()
    while (true) {
        if (
            Files.isRegularFile(current.resolve("settings.gradle.kts")) &&
                Files.isDirectory(current.resolve("game")) &&
                Files.isDirectory(current.resolve("data")) &&
                Files.isRegularFile(current.resolve("config/headless_manifest.toml"))
        ) {
            return current
        }
        current = current.parent ?: break
    }
    error("Unable to locate repository root from ${Paths.get("").toAbsolutePath().normalize()}.")
}

internal fun resolvePath(root: Path, path: String): Path {
    val normalized = path.replace('\\', '/').removePrefix("./")
    val candidate = Path.of(normalized)
    return if (candidate.isAbsolute) {
        candidate.normalize()
    } else {
        root.resolve(normalized).normalize()
    }
}


