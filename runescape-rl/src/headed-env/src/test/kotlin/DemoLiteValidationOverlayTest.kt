import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class DemoLiteValidationOverlayTest {
    @Test
    fun `overlay surfaces movement intent and player state from runtime state`() {
        val overlay = DemoLiteValidationOverlay(sessionIdProvider = { "demo-lite-test-session" }) { _, _ -> }
        overlay.recordEvent(
            event(
                type = "starter_state_manifest_written",
                fields =
                    linkedMapOf(
                        "path" to "/tmp/demo-lite/starter.json",
                        "manifest" to
                            linkedMapOf(
                                "observed_wave" to 1,
                                "ammo_count" to 1000,
                                "shark_count" to 20,
                                "prayer_potion_dose_count" to 32,
                                "running" to true,
                                "observed_player_tile" to linkedMapOf("x" to 6509, "y" to 125, "level" to 0),
                            ),
                    ),
            ),
        )
        overlay.recordEvent(
            event(
                type = "reset_complete",
                tick = 0,
                fields =
                    linkedMapOf(
                        "runtime_phase" to "reset_complete",
                        "cause" to "startup",
                        "terminal_code" to "NONE",
                        "wave" to 1,
                    ),
            ),
        )
        overlay.renderState(sampleState())

        val snapshot = overlay.snapshotForTest()
        assertContains(snapshot.sessionText, "Session: demo-lite-test-session")
        assertContains(snapshot.sessionText, "Starter manifest: /tmp/demo-lite/starter.json")
        assertContains(snapshot.sessionText, "Ammo: 1000")
        assertContains(snapshot.movementText, "current_tile: (6503, 103, 0)")
        assertContains(snapshot.movementText, "destination_tile: (6496, 96, 0)")
        assertContains(snapshot.movementText, "movement_source: reset_spawn")
        assertContains(snapshot.playerText, "Attack resolution: moved_or_pathed_toward_target")
        assertContains(snapshot.npcText, "Visible NPC count: 1")
        assertContains(snapshot.npcText, "lifecycle=npc_newly_visible")
    }

    @Test
    fun `overlay event console surfaces spawn semantics action lifecycle and shutdown reason`() {
        val overlay = DemoLiteValidationOverlay(sessionIdProvider = { "demo-lite-test-session" }) { _, _ -> }

        overlay.recordEvent(
            event(
                type = "startup_movement_intent",
                tick = 1,
                fields =
                    linkedMapOf(
                        "destination_tile" to linkedMapOf("x" to 6496, "y" to 96, "level" to 0),
                    ),
            ),
        )
        overlay.recordEvent(
            event(
                type = "state_delta",
                tick = 1,
                fields =
                    linkedMapOf(
                        "changes" to
                            listOf(
                                linkedMapOf(
                                    "field" to "npc_tracking_added",
                                    "to" to
                                        listOf(
                                            linkedMapOf(
                                                "classification" to "wave_start_world_spawn",
                                                "npc" to linkedMapOf("npc_index" to 1, "id" to "tz_kih"),
                                            ),
                                        ),
                                ),
                            ),
                    ),
            ),
        )
        overlay.recordEvent(
            event(
                type = "action_handled",
                tick = 12,
                fields =
                    linkedMapOf(
                        "label" to "attack_visible_npc",
                        "deferred" to true,
                        "action_result" to
                            linkedMapOf(
                                "action_applied" to true,
                                "rejection_reason" to null,
                                "metadata" to linkedMapOf("attack_resolution" to "moved_or_pathed_toward_target"),
                            ),
                    ),
            ),
        )
        overlay.recordEvent(
            event(
                type = "input_discarded",
                tick = 13,
                fields =
                    linkedMapOf(
                        "discard_reason" to "discarded_due_to_shutdown",
                        "request" to linkedMapOf("label" to "walk_to_tile"),
                    ),
            ),
        )
        overlay.recordEvent(
            event(
                type = "session_end",
                tick = 13,
                fields = linkedMapOf("session_end_reason" to "user_window_close"),
            ),
        )

        val snapshot = overlay.snapshotForTest()
        assertContains(snapshot.eventConsoleText, "startup movement attributed to shared reset_spawn")
        assertContains(snapshot.eventConsoleText, "wave_spawn: tz_kih#1")
        assertContains(snapshot.eventConsoleText, "attack_visible_npc accepted")
        assertContains(snapshot.eventConsoleText, "walk_to_tile discarded_due_to_shutdown")
        assertContains(snapshot.eventConsoleText, "session end: user_window_close")
        assertContains(snapshot.sessionText, "Session/shutdown reason: user_window_close")
    }

    private fun sampleState(): DemoLiteTickState =
        DemoLiteTickState(
            observation =
                HeadlessObservationV1(
                    tick = 12,
                    episodeSeed = 91_001L,
                    player =
                        HeadlessObservationPlayer(
                            tile = HeadlessObservationTile(6503, 103, 0),
                            hitpointsCurrent = 700,
                            hitpointsMax = 700,
                            prayerCurrent = 43,
                            prayerMax = 43,
                            runEnergy = 10_000,
                            runEnergyMax = 10_000,
                            runEnergyPercent = 100,
                            running = true,
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
                                    busyLocked = true,
                                ),
                            consumables =
                                HeadlessObservationConsumables(
                                    sharkCount = 20,
                                    prayerPotionDoseCount = 32,
                                    ammoId = "adamant_bolts",
                                    ammoCount = 1000,
                                ),
                        ),
                    wave = HeadlessObservationWave(wave = 1, rotation = 14, remaining = 1),
                    npcs =
                        listOf(
                            HeadlessObservationNpc(
                                visibleIndex = 0,
                                npcIndex = 1,
                                id = "tz_kih",
                                tile = HeadlessObservationTile(6491, 95, 0),
                                hitpointsCurrent = 100,
                                hitpointsMax = 100,
                                hidden = false,
                                dead = false,
                                underAttack = false,
                                jadTelegraphState = 0,
                            ),
                        ),
                ),
            visibleTargets =
                listOf(
                    HeadlessVisibleNpcTarget(
                        visibleIndex = 0,
                        npcIndex = 1,
                        id = "tz_kih",
                        tile = world.gregs.voidps.type.Tile(6491, 95, 0),
                    ),
                ),
            trackedNpcs =
                listOf(
                    DemoLiteTrackedNpcSnapshot(
                        npcIndex = 1,
                        id = "tz_kih",
                        tile = HeadlessObservationTile(6491, 95, 0),
                        hidden = false,
                        dead = false,
                        underAttack = false,
                        visibleToPlayer = true,
                    ),
                ),
            movement =
                DemoLiteMovementSnapshot(
                    currentTile = HeadlessObservationTile(6503, 103, 0),
                    destinationTile = HeadlessObservationTile(6496, 96, 0),
                    movementState = "pathing",
                    movementSource = "reset_spawn",
                    movementSourceDetail = "FightCaveEpisodeInitializer.resetFightCaveInstance -> tele(entrance) + walkTo(fightCave.centre)",
                    mode = "Movement",
                ),
            lastAction = HeadlessAction.AttackVisibleNpc(0),
            lastActionResult =
                HeadlessActionResult(
                    actionType = HeadlessActionType.AttackVisibleNpc,
                    actionId = HeadlessActionType.AttackVisibleNpc.id,
                    actionApplied = true,
                    metadata =
                        linkedMapOf(
                            "attack_resolution" to "moved_or_pathed_toward_target",
                            "destination_tile" to "Tile(6496, 96, 0)",
                        ),
                ),
            terminalCode = DemoLiteTerminalCode.NONE,
            resetCount = 1,
            sessionSeed = 91_001L,
            tickCap = 20_000,
        )

    private fun event(
        type: String,
        tick: Int? = null,
        fields: LinkedHashMap<String, Any?> = linkedMapOf(),
    ): DemoLiteSessionEvent {
        val payload =
            linkedMapOf<String, Any?>(
                "event_type" to type,
                "timestamp_millis" to 1_000L,
                "session_id" to "demo-lite-test-session",
                "episode_id" to "episode-0001",
                "tick" to tick,
            ).apply {
                putAll(fields)
            }
        return DemoLiteSessionEvent(
            eventType = type,
            timestampMillis = 1_000L,
            sessionId = "demo-lite-test-session",
            episodeId = "episode-0001",
            tick = tick,
            payload = payload,
        )
    }
}
