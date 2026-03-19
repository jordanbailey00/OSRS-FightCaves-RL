import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class HeadlessLoadsFightCaveDataOnlyTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `headless runtime loads only allowlisted data files`() {
        val runtime = HeadlessMain.bootstrap(
            loadContentScripts = false,
            startWorld = false,
            installShutdownHook = false,
            settingsOverrides = headlessTestOverrides(),
        )

        val loadedFiles =
            runtime.configFiles.map.values
                .flatten()
                .map { Path.of(it).toAbsolutePath().normalize().toString().replace('\\', '/') }
                .toSet()

        val allowlistedFiles =
            resolveAllowlistedDataFiles(runtime.allowlist)
                .map { it.toString().replace('\\', '/') }
                .toSet()

        assertTrue(loadedFiles.isNotEmpty(), "Expected headless data loader to resolve at least one allowlisted file.")
        assertEquals(allowlistedFiles, loadedFiles)
        assertTrue(loadedFiles.none { it.contains("/data/social/") }, "Social data should not be loaded unless explicitly allowlisted.")

        runtime.tick()
    }
}
