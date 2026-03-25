import org.junit.jupiter.api.Test
import java.nio.file.Files
import world.gregs.voidps.type.Tile
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertTrue

class DemoLiteSessionLoggerTest {
    @Test
    fun `logger creates persistent session log and summary artifacts`() {
        withLogger { logger ->
            logger.logSessionStart()
            logger.logRuntimeInitialized()
        }.also { (logText, summaryText) ->
            assertContains(logText, "\"event_type\":\"session_start\"")
            assertContains(logText, "\"event_type\":\"runtime_initialized\"")
            assertContains(summaryText, "\"session_log_path\"")
            assertContains(summaryText, "\"session_summary_path\"")
        }
    }

    @Test
    fun `logger captures idle ticks and accepted rejected actions`() {
        withLogger { logger ->
            logger.logSessionStart()

            val idleState = tickState(tick = 100, resetCount = 1, lastAction = HeadlessAction.Wait, lastActionResult = HeadlessActionResult(HeadlessActionType.Wait, 0, true))
            logger.logActionHandled(phase = "idle_tick", request = null, state = idleState)
            logger.logTick(phase = "idle_tick", previousState = null, state = idleState, request = null)

            val request =
                DemoLiteActionRequest(
                    requestId = 42L,
                    label = "eat_shark",
                    source = "ui.button.eat_shark",
                    action = HeadlessAction.EatShark,
                    requestedAtTick = 100,
                )
            logger.logInputRequested(request, idleState)
            val rejectedState =
                tickState(
                    tick = 101,
                    resetCount = 1,
                    lastAction = HeadlessAction.EatShark,
                    lastActionResult =
                        HeadlessActionResult(
                            actionType = HeadlessActionType.EatShark,
                            actionId = HeadlessActionType.EatShark.id,
                            actionApplied = false,
                            rejectionReason = HeadlessActionRejectReason.MissingConsumable,
                        ),
                    sharkCount = 0,
                )
            logger.logActionHandled(phase = "action_tick", request = request, state = rejectedState)
            logger.logTick(phase = "action_tick", previousState = idleState, state = rejectedState, request = request)
        }.also { (logText, summaryText) ->
            assertContains(logText, "\"event_type\":\"tick_state\"")
            assertContains(logText, "\"runtime_phase\":\"idle_tick\"")
            assertContains(logText, "\"source\":\"runtime.idle_wait\"")
            assertContains(logText, "\"event_type\":\"input_requested\"")
            assertContains(logText, "\"event_type\":\"action_handled\"")
            assertContains(logText, "\"rejection_reason\":\"MissingConsumable\"")
            assertContains(summaryText, "\"action_rejected_count\": 1")
        }
    }

    @Test
    fun `logger captures terminal and reset events`() {
        withLogger { logger ->
            logger.logSessionStart()

            val activeState = tickState(tick = 150, resetCount = 1)
            val terminalState =
                tickState(
                    tick = 151,
                    resetCount = 1,
                    terminalCode = DemoLiteTerminalCode.PLAYER_DEATH,
                    hitpointsCurrent = 0,
                )
            logger.logTick(phase = "terminal_hold", previousState = activeState, state = terminalState, request = null)
            logger.logResetDecision(
                decision = DemoLiteResetDecision(resetNow = true, autoResetRemainingMillis = 0L, reason = "player_death"),
                state = terminalState,
                phase = "pre_tick",
            )
            logger.logResetBegin("player_death", terminalState)
            val manifestPath = logger.writeStarterStateManifest("episode-0002", starterManifest())
            val resetState = tickState(tick = 200, resetCount = 2)
            logger.logResetComplete("player_death", resetState, manifestPath)
        }.also { (logText, summaryText) ->
            assertContains(logText, "\"event_type\":\"terminal_emitted\"")
            assertContains(logText, "\"event_type\":\"reset_decision\"")
            assertContains(logText, "\"event_type\":\"reset_begin\"")
            assertContains(logText, "\"event_type\":\"reset_complete\"")
            assertContains(logText, "\"event_type\":\"starter_state_manifest_written\"")
            assertContains(summaryText, "\"PLAYER_DEATH\": 1")
            assertContains(summaryText, "\"player_death\": 1")
        }
    }

    @Test
    fun `logger captures startup movement shutdown reason and discarded inputs`() {
        withLogger { logger ->
            logger.logSessionStart()
            val startupState =
                tickState(
                    tick = 0,
                    resetCount = 1,
                    movementDestinationTile = HeadlessObservationTile(6509, 117, 0),
                    movementState = "pathing",
                    movementSource = "reset_spawn",
                    movementSourceDetail = "FightCaveEpisodeInitializer.resetFightCaveInstance -> tele(entrance) + walkTo(fightCave.centre)",
                )
            logger.logTick(phase = "reset_complete", previousState = null, state = startupState, request = null)
            val pendingRequest =
                DemoLiteActionRequest(
                    requestId = 77L,
                    label = "walk_to_tile",
                    source = "ui.button.walk_east",
                    action = HeadlessAction.WalkToTile(Tile(6510, 125, 0)),
                    payload = mapOf("target_tile" to "6510,125,0"),
                    requestedAtTick = 0,
                )
            logger.logInputRequested(pendingRequest, startupState)
            logger.logInputDiscarded(
                request = pendingRequest,
                reason = "discarded_due_to_shutdown",
                currentState = startupState,
                fields = linkedMapOf("shutdown_reason" to "user_window_close"),
            )
            logger.closeWithReason(reason = "user_window_close", state = startupState)
        }.also { (logText, summaryText) ->
            assertContains(logText, "\"event_type\":\"startup_movement_intent\"")
            assertContains(logText, "\"movement_source\":\"reset_spawn\"")
            assertContains(logText, "\"event_type\":\"input_discarded\"")
            assertContains(logText, "\"discard_reason\":\"discarded_due_to_shutdown\"")
            assertContains(logText, "\"session_end_reason\":\"user_window_close\"")
            assertContains(summaryText, "\"action_discarded_count\": 1")
            assertContains(summaryText, "\"session_end_reason\": \"user_window_close\"")
        }
    }

    private fun withLogger(block: (DemoLiteSessionLogger) -> Unit): Pair<String, String> {
        val tempRoot = Files.createTempDirectory("demo-lite-logger-test")
        val previousLogRoot = System.getProperty("demoLiteSessionLogRoot")
        val previousModuleDir = System.getProperty("demoLiteModuleDir")
        System.setProperty("demoLiteSessionLogRoot", tempRoot.toAbsolutePath().toString())
        System.setProperty("demoLiteModuleDir", tempRoot.toAbsolutePath().toString())
        try {
            val logger = DemoLiteSessionLogger.create(DemoLiteConfig())
            logger.use(block)
            val logText = logger.sessionLogPath.readText()
            val summaryText = logger.sessionSummaryPath.readText()
            assertTrue(Files.isDirectory(logger.sessionDirectory))
            return logText to summaryText
        } finally {
            restoreProperty("demoLiteSessionLogRoot", previousLogRoot)
            restoreProperty("demoLiteModuleDir", previousModuleDir)
        }
    }

    private fun restoreProperty(name: String, value: String?) {
        if (value == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, value)
        }
    }

    private fun tickState(
        tick: Int,
        resetCount: Int,
        lastAction: HeadlessAction? = HeadlessAction.Wait,
        lastActionResult: HeadlessActionResult? = HeadlessActionResult(HeadlessActionType.Wait, 0, true),
        terminalCode: DemoLiteTerminalCode = DemoLiteTerminalCode.NONE,
        hitpointsCurrent: Int = 700,
        prayerCurrent: Int = 43,
        ammoCount: Int = 1_000,
        sharkCount: Int = 20,
        prayerPotionDoseCount: Int = 32,
        running: Boolean = true,
        wave: Int = 1,
        remaining: Int = 1,
        movementDestinationTile: HeadlessObservationTile? = null,
        movementState: String = "idle",
        movementSource: String = "other",
        movementSourceDetail: String? = null,
    ): DemoLiteTickState =
        DemoLiteTickState(
            observation =
                HeadlessObservationV1(
                    tick = tick,
                    episodeSeed = 91_000L + resetCount,
                    player =
                        HeadlessObservationPlayer(
                            tile = HeadlessObservationTile(6509, 125, 0),
                            hitpointsCurrent = hitpointsCurrent,
                            hitpointsMax = 700,
                            prayerCurrent = prayerCurrent,
                            prayerMax = 43,
                            runEnergy = 10_000,
                            runEnergyMax = 10_000,
                            runEnergyPercent = 100,
                            running = running,
                            protectionPrayers =
                                HeadlessObservationProtectionPrayers(
                                    protectFromMagic = false,
                                    protectFromMissiles = false,
                                    protectFromMelee = false,
                                ),
                            lockouts =
                                HeadlessObservationLockouts(
                                    attackLocked = false,
                                    foodLocked = false,
                                    drinkLocked = false,
                                    comboLocked = false,
                                    busyLocked = false,
                                ),
                            consumables =
                                HeadlessObservationConsumables(
                                    sharkCount = sharkCount,
                                    prayerPotionDoseCount = prayerPotionDoseCount,
                                    ammoId = "adamant_bolts",
                                    ammoCount = ammoCount,
                                ),
                        ),
                    wave = HeadlessObservationWave(wave = wave, rotation = 14, remaining = remaining),
                    npcs =
                        listOf(
                            HeadlessObservationNpc(
                                visibleIndex = 0,
                                npcIndex = 1,
                                id = "tz_haar_xil",
                                tile = HeadlessObservationTile(6510, 125, 0),
                                hitpointsCurrent = 40,
                                hitpointsMax = 40,
                                hidden = false,
                                dead = false,
                                underAttack = false,
                                jadTelegraphState = 0,
                            )
                        ),
                ),
            visibleTargets =
                listOf(
                    HeadlessVisibleNpcTarget(
                        visibleIndex = 0,
                        npcIndex = 1,
                        id = "tz_haar_xil",
                        tile = world.gregs.voidps.type.Tile(6510, 125, 0),
                    )
                ),
            trackedNpcs =
                listOf(
                    DemoLiteTrackedNpcSnapshot(
                        npcIndex = 1,
                        id = "tz_haar_xil",
                        tile = HeadlessObservationTile(6510, 125, 0),
                        hidden = false,
                        dead = false,
                        underAttack = false,
                        visibleToPlayer = true,
                    )
                ),
            movement =
                DemoLiteMovementSnapshot(
                    currentTile = HeadlessObservationTile(6509, 125, 0),
                    destinationTile = movementDestinationTile,
                    movementState = movementState,
                    movementSource = movementSource,
                    movementSourceDetail = movementSourceDetail,
                    mode = if (movementDestinationTile == null) "EmptyMode" else "Movement",
                ),
            lastAction = lastAction,
            lastActionResult = lastActionResult,
            terminalCode = terminalCode,
            resetCount = resetCount,
            sessionSeed = 91_000L + resetCount,
            tickCap = 20_000,
        )

    private fun starterManifest(): DemoLiteStarterStateManifest =
        DemoLiteStarterStateManifest(
            derivedFromSharedInitializer = true,
            sharedInitializerPath = "FightCaveEpisodeInitializer.reset",
            startWaveRequested = 1,
            episodeSeed = 91_002L,
            instanceId = 12345,
            resetStatePlayerTile = HeadlessObservationTile(6509, 125, 0),
            observedPlayerTile = HeadlessObservationTile(6509, 125, 0),
            observedWave = 1,
            observedRotation = 14,
            observedRemaining = 1,
            equipment =
                linkedMapOf(
                    "hat" to DemoLiteItemManifestEntry("coif", 1),
                    "weapon" to DemoLiteItemManifestEntry("rune_crossbow", 1),
                    "chest" to DemoLiteItemManifestEntry("black_dragonhide_body", 1),
                    "legs" to DemoLiteItemManifestEntry("black_dragonhide_chaps", 1),
                    "hands" to DemoLiteItemManifestEntry("black_dragonhide_vambraces", 1),
                    "feet" to DemoLiteItemManifestEntry("snakeskin_boots", 1),
                    "ammo" to DemoLiteItemManifestEntry("adamant_bolts", 1_000),
                    "neck" to null,
                ),
            inventory =
                linkedMapOf(
                    "prayer_potion_4" to DemoLiteItemManifestEntry("prayer_potion_4", 8),
                    "shark" to DemoLiteItemManifestEntry("shark", 20),
                ),
            skills =
                linkedMapOf(
                    "attack" to DemoLiteSkillManifestEntry(1, 0, true),
                    "constitution" to DemoLiteSkillManifestEntry(700, 7_376_277, true),
                ),
            hitpointsDisplay = 70,
            constitutionCurrent = 700,
            constitutionMax = 700,
            prayerCurrent = 43,
            prayerMax = 43,
            runEnergy = 10_000,
            runEnergyMax = 10_000,
            runEnergyPercent = 100,
            running = true,
            movementMode = "run",
            skipLevelUp = true,
            noXpGainConfigured = true,
            blockedSkills = listOf("attack", "constitution"),
            ammoId = "adamant_bolts",
            ammoCount = 1_000,
            sharkCount = 20,
            prayerPotionDoseCount = 32,
        )
}
