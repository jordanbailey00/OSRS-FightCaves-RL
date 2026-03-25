import content.area.karamja.tzhaar_city.TzhaarFightCaveTest
import org.junit.jupiter.api.Test
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class HeadedModeStillPassesBaselineFightCaveTests {

    @Test
    fun `headed baseline fight cave tests still pass`() {
        val request =
            LauncherDiscoveryRequestBuilder
                .request()
                .selectors(selectClass(TzhaarFightCaveTest::class.java))
                .build()

        val summaryListener = SummaryGeneratingListener()
        val launcher = LauncherFactory.create()
        launcher.registerTestExecutionListeners(summaryListener)
        launcher.execute(request)

        val summary = summaryListener.summary
        assertEquals(0, summary.testsFailedCount, "Expected no failures in headed Fight Caves baseline tests.")
        assertTrue(summary.testsSucceededCount > 0, "Expected headed Fight Caves baseline tests to execute.")
    }
}