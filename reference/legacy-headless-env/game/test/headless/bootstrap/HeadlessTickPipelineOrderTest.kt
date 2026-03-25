import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class HeadlessTickPipelineOrderTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `headless tick pipeline preserves required stage order`() {
        val runtime = HeadlessMain.bootstrap(
            loadContentScripts = false,
            startWorld = false,
            installShutdownHook = false,
            settingsOverrides = headlessTestOverrides(),
        )

        assertEquals(headlessTickStageOrder, describeTickStages(runtime.stages))

        runtime.tick()
    }
}
