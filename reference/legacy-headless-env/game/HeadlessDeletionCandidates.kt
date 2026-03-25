import sim.config.ConfigReader
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class HeadlessDeletionCandidateReport(
    val generatedAtUtc: String,
    val sourceManifest: String,
    val candidateModuleDirectories: List<String>,
    val excludedRuntimeStages: List<String>,
    val candidateCodeFiles: List<String>,
    val candidateDataFiles: List<String>,
    val candidateCodeDirectories: List<String>,
    val candidateDataDirectories: List<String>,
)

object HeadlessDeletionCandidates {

    fun generate(root: Path = locateRepositoryRoot()): HeadlessDeletionCandidateReport {
        val manifest = loadManifest(root)

        val moduleCandidates =
            (manifest.excludedModules + manifest.optionalModules)
                .distinct()
                .filter { Files.isDirectory(root.resolve(it)) }
                .sorted()

        val codeKeepMatchers = compileMatchers(manifest.requiredCodeGlobs + manifest.codeExceptionGlobs + manifest.oracleCodeGlobs)
        val dataKeepMatchers = compileMatchers(manifest.dataExceptionGlobs)

        val candidateCodeFiles =
            collectFiles(root, "game/content")
                .filterNot { path ->
                    path in manifest.requiredCodeFiles || matchesAny(path, codeKeepMatchers)
                }.sorted()

        val candidateDataFiles =
            collectFiles(root, "data")
                .filterNot { path ->
                    path in manifest.requiredDataFiles || path in manifest.optionalDataFiles || path in manifest.oracleDataFiles || matchesAny(path, dataKeepMatchers)
                }.sorted()

        val candidateCodeDirectories = candidateCodeFiles.map { it.substringBeforeLast('/') }.distinct().sorted()
        val candidateDataDirectories = candidateDataFiles.map { it.substringBeforeLast('/') }.distinct().sorted()

        return HeadlessDeletionCandidateReport(
            generatedAtUtc = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            sourceManifest = "config/headless_manifest.toml",
            candidateModuleDirectories = moduleCandidates,
            excludedRuntimeStages = manifest.excludedStages.sorted(),
            candidateCodeFiles = candidateCodeFiles,
            candidateDataFiles = candidateDataFiles,
            candidateCodeDirectories = candidateCodeDirectories,
            candidateDataDirectories = candidateDataDirectories,
        )
    }

    fun writeMarkdown(report: HeadlessDeletionCandidateReport, output: Path = locateRepositoryRoot().resolve("history/deletion_candidates.md")): Path {
        Files.createDirectories(output.parent)
        val markdown =
            buildString {
                appendLine("# Deletion Candidates (Step 10)")
                appendLine()
                appendLine("Generated from `${report.sourceManifest}` at `${report.generatedAtUtc}`.")
                appendLine()

                appendLine("## Candidate Module Directories")
                appendLine()
                appendLine("${report.candidateModuleDirectories.size} module directories are candidates (excluded or optional in manifest):")
                appendLine()
                report.candidateModuleDirectories.forEach { appendLine("- `$it`") }
                appendLine()

                appendLine("## Runtime Stage Exclusions")
                appendLine()
                appendLine("Headless runtime must keep these tick stages disabled:")
                appendLine()
                report.excludedRuntimeStages.forEach { appendLine("- `$it`") }
                appendLine()

                appendLine("## Candidate Code Directories")
                appendLine()
                appendLine("${report.candidateCodeDirectories.size} directories under `game/content` are outside the current keep closure:")
                appendLine()
                report.candidateCodeDirectories.forEach { appendLine("- `$it`") }
                appendLine()

                appendLine("## Candidate Code Files")
                appendLine()
                appendLine("${report.candidateCodeFiles.size} files under `game/content` are outside the current keep closure:")
                appendLine()
                report.candidateCodeFiles.forEach { appendLine("- `$it`") }
                appendLine()

                appendLine("## Candidate Data Directories")
                appendLine()
                appendLine("${report.candidateDataDirectories.size} directories under `data/` are outside the current keep closure:")
                appendLine()
                report.candidateDataDirectories.forEach { appendLine("- `$it`") }
                appendLine()

                appendLine("## Candidate Data Files")
                appendLine()
                appendLine("${report.candidateDataFiles.size} files under `data/` are outside the current keep closure:")
                appendLine()
                report.candidateDataFiles.forEach { appendLine("- `$it`") }
                appendLine()

                appendLine("## Notes")
                appendLine()
                appendLine("- This is a candidate inventory for Step 10/13 review, not an auto-delete operation.")
                appendLine("- Any candidate required for headless runtime, oracle parity harness, tests, build, or CI must be removed from this list before hard deletion.")
            }
        Files.writeString(output, markdown)
        return output
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val report = generate()
        val output = writeMarkdown(report)
        println(
            "Generated deletion candidates: modules=${report.candidateModuleDirectories.size}, codeFiles=${report.candidateCodeFiles.size}, dataFiles=${report.candidateDataFiles.size}, output=$output",
        )
    }

    private data class ManifestProjection(
        val excludedModules: List<String>,
        val optionalModules: List<String>,
        val excludedStages: List<String>,
        val requiredCodeFiles: List<String>,
        val requiredCodeGlobs: List<String>,
        val oracleCodeGlobs: List<String>,
        val codeExceptionGlobs: List<String>,
        val requiredDataFiles: List<String>,
        val optionalDataFiles: List<String>,
        val oracleDataFiles: List<String>,
        val dataExceptionGlobs: List<String>,
    )

    private fun loadManifest(root: Path): ManifestProjection {
        val source = root.resolve("config/headless_manifest.toml")
        require(Files.isRegularFile(source)) { "Missing manifest file: $source" }

        val sections =
            BufferedInputStream(Files.newInputStream(source)).use { stream ->
                ConfigReader(stream, 4096, source.toString()).use { reader ->
                    reader.sections(expectedSections = 24, expectedSize = 64)
                }
            }

        fun strings(section: String, key: String): List<String> {
            val map = sections[section] ?: return emptyList()
            val value = map[key] ?: return emptyList()
            val list = value as? List<*> ?: error("Expected [$section].$key to be a list.")
            return list.map { entry -> entry as? String ?: error("Expected [$section].$key entries to be strings.") }
        }

        return ManifestProjection(
            excludedModules = strings("modules", "excluded"),
            optionalModules = strings("modules", "optional"),
            excludedStages = strings("tick_pipeline", "headless_candidate_excluded"),
            requiredCodeFiles = strings("code_paths", "required_files"),
            requiredCodeGlobs = strings("code_paths", "required_globs"),
            oracleCodeGlobs = strings("code_paths", "oracle_only_globs"),
            codeExceptionGlobs = strings("code_paths", "excluded_file_exceptions"),
            requiredDataFiles = strings("data_files", "required"),
            optionalDataFiles = strings("data_files", "optional"),
            oracleDataFiles = strings("data_files", "oracle_only"),
            dataExceptionGlobs = strings("data_files", "excluded_file_exceptions"),
        )
    }

    private fun collectFiles(root: Path, relativeDirectory: String): List<String> {
        val base = root.resolve(relativeDirectory)
        if (!Files.isDirectory(base)) {
            return emptyList()
        }
        val files = mutableListOf<String>()
        Files.walk(base).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (!Files.isRegularFile(path)) {
                    continue
                }
                files += toRelativeNormalized(root, path)
            }
        }
        return files
    }

    private fun compileMatchers(globs: List<String>) =
        globs
            .distinct()
            .map { glob ->
                val platformGlob = glob.replace('/', File.separatorChar)
                FileSystems.getDefault().getPathMatcher("glob:$platformGlob")
            }

    private fun matchesAny(relativePath: String, matchers: List<java.nio.file.PathMatcher>): Boolean {
        if (matchers.isEmpty()) {
            return false
        }
        val candidate = Paths.get(relativePath.replace('/', File.separatorChar))
        return matchers.any { matcher -> matcher.matches(candidate) }
    }

    private fun toRelativeNormalized(root: Path, path: Path): String =
        root
            .relativize(path)
            .toString()
            .replace('\\', '/')

    private fun locateRepositoryRoot(): Path {
        var current = Paths.get("").toAbsolutePath().normalize()
        while (true) {
            if (
                Files.isRegularFile(current.resolve("settings.gradle.kts")) &&
                    Files.isDirectory(current.resolve("game")) &&
                    Files.isRegularFile(current.resolve("config/headless_manifest.toml"))
            ) {
                return current
            }
            current = current.parent ?: break
        }
        error("Unable to locate repository root from ${Paths.get("").toAbsolutePath().normalize()}.")
    }
}
