import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import sim.engine.data.Settings
import sim.engine.get
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class HeadlessBootWithoutNetworkTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `boot headless runtime without starting network stack`() {
        val runtime = HeadlessMain.bootstrap(
            loadContentScripts = false,
            startWorld = false,
            installShutdownHook = false,
            settingsOverrides = headlessTestOverrides(),
        )

        assertNotNull(runtime)
        assertEquals(RuntimeMode.Headless.id, Settings[RUNTIME_MODE_KEY, RuntimeMode.Headed.id])

        val config: HeadlessRuntimeConfig = get()
        assertFalse(config.networkingEnabled)

        val stageNames = describeTickStages(runtime.stages)
        assertFalse(stageNames.contains("ConnectionQueue"), "Headless runtime should not include ConnectionQueue stage.")
        assertFalse(stageNames.contains("FloorItems"), "Headless runtime should not include FloorItems stage.")

        val serverField = Main::class.java.getDeclaredField("server")
        serverField.isAccessible = true
        assertNull(serverField.get(Main), "Headless boot should not initialize headed GameServer.")

        runtime.tick()
    }
}


