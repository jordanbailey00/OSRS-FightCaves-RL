import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ForbiddenPathsAbsentTest {

    @Test
    fun `forbidden paths are absent from repository tree`() {
        val root = locateRepositoryRoot()
        val manifest = loadHeadlessPruneManifest(root)

        for (relativePath in manifest.forbiddenPaths) {
            val path = root.resolve(relativePath)
            assertFalse(Files.exists(path), "Forbidden path exists after prune: $relativePath")
        }

        val pruneReport = root.resolve("history/repo_prune_report.md")
        assertTrue(Files.isRegularFile(pruneReport), "Missing Step 13 prune report artifact: $pruneReport")
    }
}
