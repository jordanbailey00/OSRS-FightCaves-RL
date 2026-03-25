import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ProjectTreeMatchesApprovedManifestTest {

    @Test
    fun `project tree aligns with approved prune manifest`() {
        val root = locateRepositoryRoot()
        val manifest = loadHeadlessPruneManifest(root)

        for (relativePath in manifest.requiredPaths) {
            val path = root.resolve(relativePath)
            assertTrue(Files.exists(path), "Required path missing after prune: $relativePath")
        }

        for (relativePath in manifest.approvedDeletedPaths) {
            val path = root.resolve(relativePath)
            assertFalse(Files.exists(path), "Approved deleted path still exists: $relativePath")
        }

        val settings = Files.readString(root.resolve("settings.gradle.kts"))
        assertFalse(settings.contains("include(\"database\")"), "settings.gradle.kts should not include database module after prune.")
        assertFalse(settings.contains("include(\"tools\")"), "settings.gradle.kts should not include tools module after prune.")

        val prunedTestRoot = root.resolve(manifest.prunedTestRoot)
        val retainedActual =
            if (!Files.isDirectory(prunedTestRoot)) {
                emptyList()
            } else {
                Files.walk(prunedTestRoot).use { stream ->
                    val iterator = stream.iterator()
                    val files = mutableListOf<String>()
                    while (iterator.hasNext()) {
                        val candidate = iterator.next()
                        if (Files.isRegularFile(candidate)) {
                            files += root.relativize(candidate).toString().replace('\\', '/')
                        }
                    }
                    files.sorted()
                }
            }
        assertEquals(manifest.retainedTestFiles.sorted(), retainedActual, "Unexpected retained files under ${manifest.prunedTestRoot}.")
    }
}
