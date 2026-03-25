import sim.config.ConfigReader
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path

data class HeadlessPruneManifest(
    val requiredPaths: List<String>,
    val approvedDeletedPaths: List<String>,
    val forbiddenPaths: List<String>,
    val prunedTestRoot: String,
    val retainedTestFiles: List<String>,
)

internal fun loadHeadlessPruneManifest(root: Path = locateRepositoryRoot()): HeadlessPruneManifest {
    val source = root.resolve("config/headless_prune_manifest.toml")
    require(Files.isRegularFile(source)) { "Missing prune manifest: $source" }

    val sections =
        BufferedInputStream(Files.newInputStream(source)).use { stream ->
            ConfigReader(stream, 512, source.toString()).use { reader ->
                reader.sections(expectedSections = 2, expectedSize = 8)
            }
        }

    fun list(section: String, key: String): List<String> {
        val map = sections[section] ?: error("Missing section [$section] in $source")
        val value = map[key] ?: error("Missing key [$section].$key in $source")
        val entries = value as? List<*> ?: error("Expected [$section].$key to be a list in $source")
        return entries.map { entry ->
            entry as? String ?: error("Expected [$section].$key entries to be strings in $source")
        }
    }

    fun string(section: String, key: String): String {
        val map = sections[section] ?: error("Missing section [$section] in $source")
        return map[key] as? String ?: error("Missing string [$section].$key in $source")
    }

    return HeadlessPruneManifest(
        requiredPaths = list("paths", "required"),
        approvedDeletedPaths = list("paths", "approved_deleted"),
        forbiddenPaths = list("paths", "forbidden"),
        prunedTestRoot = string("paths", "pruned_test_root"),
        retainedTestFiles = list("paths", "retained_test_files"),
    )
}
