import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertTrue
import world.gregs.voidps.type.Tile

class DemoLiteRuntimeLoggingValidationTest {
    @Test
    fun `runtime logging writes a fresh PR 7_1 validation session artifact set`() {
        val sessionRoot = DemoLiteValidationArtifactTestSupport.reportsDir.resolve("pr71-validation-session")
        Files.createDirectories(sessionRoot)

        val previousLogRoot = System.getProperty("demoLiteSessionLogRoot")
        val previousModuleDir = System.getProperty("demoLiteModuleDir")
        val moduleDir =
            Path.of(System.getProperty("user.dir"))
                .resolve("../../demo-env")
                .normalize()
                .toAbsolutePath()
        System.setProperty("demoLiteSessionLogRoot", sessionRoot.toAbsolutePath().toString())
        System.setProperty("demoLiteModuleDir", moduleDir.toString())

        val states = CopyOnWriteArrayList<DemoLiteTickState>()
        val firstState = CountDownLatch(1)
        val runtime = DemoLiteRuntime.create(DemoLiteConfig(tickMillis = 25L, autoResetDelayMillis = 0L))
        try {
            runtime.start { state ->
                states += state
                firstState.countDown()
            }
            assertTrue(firstState.await(15, TimeUnit.SECONDS), "Timed out waiting for the first demo-lite tick.")
            waitUntil("initial visible target") {
                val latest = states.lastOrNull()
                latest != null &&
                    latest.visibleTargets.isNotEmpty() &&
                    !latest.observation.player.lockouts.attackLocked
            }

            val attackState = states.last()
            val target = attackState.visibleTargets.first()
            runtime.queueAction(
                HeadlessAction.AttackVisibleNpc(target.visibleIndex),
                source = "ui.button.attack_visible_target",
                payload =
                    mapOf(
                        "visible_target_index" to target.visibleIndex,
                        "target_id" to target.id,
                    ),
            )
            waitUntil("accepted attack action") {
                states.any { state ->
                    state.lastAction is HeadlessAction.AttackVisibleNpc &&
                        state.lastActionResult?.actionApplied == true
                }
            }

            val movementState = states.last()
            runtime.queueAction(
                HeadlessAction.WalkToTile(
                    Tile(
                        movementState.observation.player.tile.x + 1,
                        movementState.observation.player.tile.y,
                        movementState.observation.player.tile.level,
                    )
                ),
                source = "ui.button.walk_east",
                payload =
                    mapOf(
                        "target_tile" to
                            "${movementState.observation.player.tile.x + 1}," +
                            "${movementState.observation.player.tile.y}," +
                            movementState.observation.player.tile.level,
                ),
            )
            runtime.close(reason = "user_window_close")
        } finally {
            runtime.close(reason = "test_cleanup")
            restoreProperty("demoLiteSessionLogRoot", previousLogRoot)
            restoreProperty("demoLiteModuleDir", previousModuleDir)
        }

        val sessionDir = latestSessionDirectory(sessionRoot)
        val sessionLog = sessionDir.resolve("session_log.jsonl")
        val sessionSummary = sessionDir.resolve("session_summary.json")
        val logText = sessionLog.readText()
        val summaryText = sessionSummary.readText()

        assertTrue(Files.isRegularFile(sessionLog))
        assertTrue(Files.isRegularFile(sessionSummary))
        assertContains(logText, "\"event_type\":\"startup_movement_intent\"")
        assertContains(logText, "\"movement_source\":\"reset_spawn\"")
        assertContains(logText, "\"field\":\"npc_tracking_added\"")
        assertContains(logText, "\"field\":\"npc_newly_visible\"")
        assertContains(logText, "\"attack_resolution\":\"")
        assertContains(logText, "\"event_type\":\"input_discarded\"")
        assertContains(logText, "\"discard_reason\":\"discarded_due_to_shutdown\"")
        assertContains(logText, "\"session_end_reason\":\"user_window_close\"")
        assertContains(summaryText, "\"session_end_reason\": \"user_window_close\"")

        DemoLiteValidationArtifactTestSupport.writeTextArtifact(
            "pr71-validation-findings.md",
            """
            # PR 7.1 Validation Findings
            
            - Startup auto-movement is attributable to the shared reset path in `FightCaveEpisodeInitializer.resetFightCaveInstance`, which teleports to the cave entrance and immediately `walkTo()`s the cave centre.
            - The temporary validation overlay now surfaces movement intent, current-vs-destination travel, NPC lifecycle labels, action lifecycle outcomes, and shutdown reason directly on screen for manual headed testing.
            - Session shutdown is now explicit and separate from in-game terminals; this validation run ended with `user_window_close`.
            - Pending input is no longer ambiguous at shutdown; unhandled requests are logged as `discarded_due_to_shutdown`.
            - Movement intent logging now records `current_tile`, `destination_tile`, movement state/source, and arrival/interruption deltas.
            - NPC change logging now distinguishes wave-start tracked additions, newly visible NPCs, and tracked removals instead of a single ambiguous `npc_spawned` field.
            - Validation session artifacts: `${sessionDir.toAbsolutePath()}`
            """.trimIndent() + "\n",
        )
    }

    private fun latestSessionDirectory(root: Path): Path =
        Files.list(root).use { stream ->
            stream
                .filter(Files::isDirectory)
                .max(Comparator.comparing { path: Path -> path.fileName.toString() })
                .orElseThrow { IllegalStateException("No demo-lite validation session directory was created under $root.") }
        }

    private fun waitUntil(label: String, timeoutMillis: Long = 15_000L, predicate: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMillis) {
            if (predicate()) {
                return
            }
            Thread.sleep(25L)
        }
        error("Timed out waiting for $label.")
    }

    private fun restoreProperty(name: String, value: String?) {
        if (value == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, value)
        }
    }
}
