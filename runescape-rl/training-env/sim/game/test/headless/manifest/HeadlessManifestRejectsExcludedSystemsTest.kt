package headless.manifest

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

internal class HeadlessManifestRejectsExcludedSystemsTest {

    private val manifest = HeadlessManifestLoader.load()

    @Test
    fun `required scripts exclude blocked namespaces`() {
        val required = manifest.strings("scripts", "required_classes")
        val oracleOnly = manifest.strings("scripts", "oracle_only_classes").toSet()

        val overlap = required.filter { it in oracleOnly }
        assertTrue(overlap.isEmpty(), "Oracle-only scripts present in required list: ${overlap.joinToString()}")

        val blockedPrefixes = listOf("content.social.", "content.activity.")
        val blocked = required.filter { entry -> blockedPrefixes.any(entry::startsWith) }
        assertTrue(blocked.isEmpty(), "Blocked script namespaces present in required list: ${blocked.joinToString()}")
    }

    @Test
    fun `required code paths are not excluded unless exception-allowlisted`() {
        val requiredPaths =
            manifest.strings("code_paths", "required_files") +
                manifest.strings("code_paths", "required_globs").map { normalize(it).removeSuffix("/**") }
        val excludedGlobs = manifest.strings("code_paths", "excluded_globs")
        val exceptions = manifest.strings("code_paths", "excluded_file_exceptions")

        val violations = violatingPaths(requiredPaths, excludedGlobs, exceptions)
        assertTrue(violations.isEmpty(), "Required code paths overlap excluded globs without exception: ${violations.joinToString()}")
    }

    @Test
    fun `required data paths are not excluded unless exception-allowlisted`() {
        val requiredPaths = manifest.strings("data_files", "required")
        val excludedGlobs = manifest.strings("data_files", "excluded_globs")
        val exceptions = manifest.strings("data_files", "excluded_file_exceptions")

        val violations = violatingPaths(requiredPaths, excludedGlobs, exceptions)
        assertTrue(violations.isEmpty(), "Required data paths overlap excluded globs without exception: ${violations.joinToString()}")
    }

    @Test
    fun `manifest declares expected excluded namespaces`() {
        val codeExcluded = manifest.strings("code_paths", "excluded_globs")
        assertTrue("game/content/social/**" in codeExcluded)
        assertTrue("game/content/activity/**" in codeExcluded)

        val dataExcluded = manifest.strings("data_files", "excluded_globs")
        assertTrue("data/social/**" in dataExcluded)
        assertTrue("data/activity/**" in dataExcluded)
    }

    private fun violatingPaths(paths: List<String>, excludedGlobs: List<String>, exceptions: List<String>): List<String> =
        paths.filter { path ->
            excludedGlobs.any { globMatches(path, it) } && exceptions.none { exception -> globMatches(path, exception) }
        }

    private fun globMatches(path: String, glob: String): Boolean {
        val value = normalize(path)
        val pattern = normalize(glob)
        return when {
            pattern.endsWith("/**") -> {
                val prefix = pattern.removeSuffix("/**")
                value == prefix || value.startsWith("$prefix/")
            }

            pattern.endsWith("/*") -> {
                val prefix = pattern.removeSuffix("/*")
                value.startsWith("$prefix/") && !value.removePrefix("$prefix/").contains('/')
            }

            else -> value == pattern
        }
    }

    private fun normalize(path: String): String = path.replace("\\", "/").trimStart('/')
}
