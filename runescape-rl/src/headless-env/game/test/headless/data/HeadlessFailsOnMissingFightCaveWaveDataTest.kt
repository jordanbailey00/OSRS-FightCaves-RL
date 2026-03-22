import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class HeadlessFailsOnMissingFightCaveWaveDataTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `headless startup fails fast when fight cave wave data is missing from allowlist`() {
        val allowlist =
            loadHeadlessDataAllowlist(
                path = headlessTestOverrides().getValue("headless.data.allowlist.path"),
            )
        val missingPath = "data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves_missing.toml"
        val mutated =
            Files.createTempFile("headless_data_allowlist_missing_waves", ".toml")
                .toAbsolutePath()
                .normalize()

        val text = Files.readString(allowlist.sourcePath)
            .replace("data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves.toml", missingPath)
        Files.writeString(mutated, text)

        val exception =
            assertFailsWith<NoSuchFileException> {
                HeadlessMain.bootstrap(
                    loadContentScripts = false,
                    startWorld = false,
                    installShutdownHook = false,
                    settingsOverrides =
                        headlessTestOverrides() +
                            mapOf("headless.data.allowlist.path" to mutated.toString()),
                )
            }

        assertTrue(exception.message?.contains(missingPath) == true)
        Files.deleteIfExists(mutated)
    }
}
