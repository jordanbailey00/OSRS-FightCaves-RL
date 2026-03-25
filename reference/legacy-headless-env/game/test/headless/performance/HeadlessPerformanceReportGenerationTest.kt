import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertTrue

internal class HeadlessPerformanceReportGenerationTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `performance benchmark log and json artifacts are generated`() {
        val tempDir = Files.createTempDirectory("headless-perf-report-test")
        val logPath = tempDir.resolve("performance_benchmark.log")
        val jsonPath = tempDir.resolve("performance_benchmark.json")
        val outputs =
            HeadlessPerformanceReportGenerator.generateAndWriteArtifacts(
                outputLog = logPath,
                outputJson = jsonPath,
                config =
                    PerformanceBenchmarkConfig(
                        throughputSteps = 128,
                        soakSteps = 256,
                        soakObserveEvery = 64,
                        batchedEnvCount = 4,
                        batchedRounds = 128,
                    ),
            )

        assertTrue(Files.isRegularFile(outputs.first), "Expected performance benchmark log at ${outputs.first}")
        assertTrue(Files.isRegularFile(outputs.second), "Expected performance benchmark json at ${outputs.second}")

        val logContent = Files.readString(logPath)
        assertTrue(logContent.contains("throughput.ticks_per_second="))
        assertTrue(logContent.contains("batched.env_steps_per_second="))
        assertTrue(logContent.contains("ceiling.workers_needed_for_100k="))

        val jsonContent = Files.readString(jsonPath)
        assertTrue(jsonContent.contains("\"throughput\""))
        assertTrue(jsonContent.contains("\"batched\""))
        assertTrue(jsonContent.contains("\"per_worker_ceiling\""))
    }
}
