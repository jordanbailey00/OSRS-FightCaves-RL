import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class HeadlessDeletionCandidateInventoryTest {

    @Test
    fun `deletion candidate report excludes required fight caves closure files`() {
        val root = locateRepositoryRoot()
        val report = HeadlessDeletionCandidates.generate(root)

        assertTrue(report.candidateModuleDirectories.isEmpty(), "Expected no module-directory candidates after Step 13 module prune.")
        assertFalse(
            report.candidateCodeFiles.contains("game/content/area/karamja/tzhaar_city/TzhaarFightCave.kt"),
            "Fight Caves controller must never be marked as deletion candidate.",
        )
        assertFalse(
            report.candidateDataFiles.contains("data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves.toml"),
            "Fight Caves waves data must never be marked as deletion candidate.",
        )

        val preview = HeadlessDeletionCandidates.writeMarkdown(report, root.resolve("temp/deletion_candidates_test.md"))
        assertTrue(Files.isRegularFile(preview), "Expected preview markdown to be generated.")
    }

    @Test
    fun `committed deletion candidate document contains required sections`() {
        val root = locateRepositoryRoot()
        val output = root.resolve("history/deletion_candidates.md")
        assertTrue(Files.isRegularFile(output), "Missing history/deletion_candidates.md artifact.")

        val markdown = Files.readString(output)
        assertTrue(markdown.contains("Candidate Module Directories"))
        assertTrue(markdown.contains("Runtime Stage Exclusions"))
    }
}
