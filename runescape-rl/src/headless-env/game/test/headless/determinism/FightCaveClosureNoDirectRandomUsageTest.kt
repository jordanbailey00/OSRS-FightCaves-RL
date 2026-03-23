import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FightCaveClosureNoDirectRandomUsageTest {

    @Test
    fun `fight cave closure does not directly use kotlin random`() {
        val root = repositoryRoot()
        val closureFiles =
            listOf(
                "game/content/area/karamja/tzhaar_city/TzhaarFightCave.kt",
                "game/content/area/karamja/tzhaar_city/TzTokJad.kt",
                "game/content/area/karamja/tzhaar_city/TzHaarHealers.kt",
                "game/content/area/karamja/tzhaar_city/TzhaarFightCaveWaves.kt",
                "game/FightCaveEpisodeInitializer.kt",
                "game/HeadlessActionAdapter.kt",
                "game/HeadlessObservationBuilder.kt",
                "game/HeadlessReplayRunner.kt",
            )

        val forbiddenTokens = listOf("import kotlin.random.Random", "kotlin.random.Random(")

        for (relative in closureFiles) {
            val file = root.resolve(relative)
            assertTrue(Files.isRegularFile(file), "Expected closure file to exist: $file")
            val source = Files.readString(file)
            for (token in forbiddenTokens) {
                assertFalse(source.contains(token), "Forbidden direct random usage token '$token' found in $relative")
            }
        }
    }

    private fun repositoryRoot(): Path {
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
