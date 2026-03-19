import content.area.karamja.tzhaar_city.JadTelegraphTrace
import content.area.karamja.tzhaar_city.TzhaarFightCave
import content.area.karamja.tzhaar_city.jadTelegraphTraceOrNull
import content.quest.instance
import headless.fast.FAST_REWARD_FEATURE_DAMAGE_DEALT_INDEX
import headless.fast.FAST_REWARD_FEATURE_DAMAGE_TAKEN_INDEX
import headless.fast.FAST_TERMINAL_NONE
import headless.fast.FastActionCodec
import headless.fast.FastDecodedAction
import headless.fast.FastEpisodeConfig
import headless.fast.FastFightCavesKernel
import headless.fast.FastFightCavesKernelDescriptor
import headless.fast.FastFlatObsWriter
import headless.fast.FastJadResolveTrace
import headless.fast.FastParityTrace
import headless.fast.FastProtectionPrayer
import headless.fast.FastRejectionCode
import headless.fast.FastResetBatchResult
import headless.fast.FastRewardFeatureWriter
import headless.fast.FastRewardSnapshot
import headless.fast.FastStepBatchResult
import headless.fast.FastStepMetrics
import headless.fast.FastTerminalState
import headless.fast.FastTerminalStateEvaluator
import headless.fast.FIGHT_CAVE_REMAINING_KEY
import headless.fast.FIGHT_CAVE_WAVE_KEY
import headless.fast.FIGHT_CAVES_FAST_REWARD_FEATURE_COUNT
import headless.fast.fightCavePrayerPotionDoseCount
import sim.engine.GameLoop
import sim.engine.data.AccountManager
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.move.running
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.get
import sim.engine.inv.equipment
import sim.engine.inv.inventory
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.types.Tile
import kotlin.system.measureNanoTime

private data class FastRuntimeSlot(
    val slotIndex: Int,
    val player: Player,
    var episodeStartTick: Int? = null,
    var episodeSteps: Int = 0,
)

class FastFightCavesKernelRuntime private constructor(
    private val runtime: HeadlessRuntime,
    private val portableKernel: FastFightCavesKernel,
    private val slots: List<FastRuntimeSlot>,
    private val tickCap: Int,
) : AutoCloseable {

    fun describe(): FastFightCavesKernelDescriptor = portableKernel.describe()

    fun resetBatch(
        configs: List<FastEpisodeConfig>,
        emitParityTrace: Boolean = false,
        observationBuffer: FloatArray? = null,
        rewardFeatureBuffer: FloatArray? = null,
    ): FastResetBatchResult =
        resetBatch(
            slotIndices = IntArray(configs.size) { it },
            configs = configs,
            emitParityTrace = emitParityTrace,
            observationBuffer = observationBuffer,
            rewardFeatureBuffer = rewardFeatureBuffer,
        )

    fun resetBatch(
        slotIndices: IntArray,
        configs: List<FastEpisodeConfig>,
        emitParityTrace: Boolean = false,
        observationBuffer: FloatArray? = null,
        rewardFeatureBuffer: FloatArray? = null,
    ): FastResetBatchResult {
        require(slotIndices.size == configs.size) {
            "Fast reset batch requires slot/config parity: ${slotIndices.size} != ${configs.size}."
        }
        val resolvedSlots = resolveSlots(slotIndices)
        val elapsedNanos =
            measureNanoTime {
                resolvedSlots.zip(configs).forEach { (slot, config) ->
                    runtime.resetFightCaveEpisode(
                        slot.player,
                        FightCaveEpisodeConfig(
                            seed = config.seed,
                            startWave = config.startWave,
                            ammo = config.ammo,
                            prayerPotions = config.prayerPotions,
                            sharks = config.sharks,
                        ),
                    )
                    slot.episodeStartTick = null
                    slot.episodeSteps = 0
                }
            }

        val players = resolvedSlots.map { it.player }
        val batch = runtime.observeFlatBatch(players)
        val flatObservations =
            FastFlatObsWriter.copyBatch(
                source = batch.values,
                envCount = resolvedSlots.size,
                featureCount = batch.featureCount,
                target = observationBuffer,
            )
        val rewardFeatures =
            FastRewardFeatureWriter.zeros(
                target = rewardFeatureBuffer,
                envCount = resolvedSlots.size,
            )
        val episodeTicks = IntArray(resolvedSlots.size)
        val episodeSteps = IntArray(resolvedSlots.size)
        val terminalCodes = IntArray(resolvedSlots.size) { FAST_TERMINAL_NONE }
        val terminated = BooleanArray(resolvedSlots.size)
        val truncated = BooleanArray(resolvedSlots.size)
        val parityTraces = mutableListOf<FastParityTrace>()

        resolvedSlots.forEachIndexed { envIndex, slot ->
            val rowOffset = envIndex * batch.featureCount
            val observationTick = flatObservations[rowOffset + 1].toInt()
            slot.episodeStartTick = observationTick
            episodeTicks[envIndex] = 0
            episodeSteps[envIndex] = 0
            if (emitParityTrace) {
                parityTraces +=
                    buildParityTrace(
                        slot = slot,
                        actionName = "reset",
                        actionAccepted = true,
                        rejectionCode = FastRejectionCode.None.code,
                        terminal = FastTerminalState.NONE,
                        damageDealt = 0f,
                        damageTaken = 0f,
                    )
            }
        }

        return FastResetBatchResult(
            envCount = resolvedSlots.size,
            slotIndices = resolvedSlots.map { it.slotIndex }.toIntArray(),
            flatObservationFeatureCount = batch.featureCount,
            rewardFeatureCount = FIGHT_CAVES_FAST_REWARD_FEATURE_COUNT,
            flatObservations = flatObservations,
            rewardFeatures = rewardFeatures,
            terminalCodes = terminalCodes,
            terminated = terminated,
            truncated = truncated,
            episodeTicks = episodeTicks,
            episodeSteps = episodeSteps,
            parityTraces = parityTraces,
            elapsedNanos = elapsedNanos,
        )
    }

    fun stepBatch(
        packedActions: IntArray,
        emitParityTrace: Boolean = false,
        observationBuffer: FloatArray? = null,
        rewardFeatureBuffer: FloatArray? = null,
    ): FastStepBatchResult =
        stepBatch(
            slotIndices = IntArray(slots.size) { it },
            packedActions = packedActions,
            emitParityTrace = emitParityTrace,
            observationBuffer = observationBuffer,
            rewardFeatureBuffer = rewardFeatureBuffer,
        )

    fun stepBatch(
        slotIndices: IntArray,
        packedActions: IntArray,
        emitParityTrace: Boolean = false,
        observationBuffer: FloatArray? = null,
        rewardFeatureBuffer: FloatArray? = null,
    ): FastStepBatchResult {
        val resolvedSlots = resolveSlots(slotIndices)
        val decodedActions = FastActionCodec.decodeBatch(packedActions, resolvedSlots.size)
        resolvedSlots.forEach(::ensureSlotReady)

        val rewardBefore = resolvedSlots.map(::captureRewardSnapshot)
        val players = resolvedSlots.map { it.player }
        val actionsForRuntime = decodedActions.map(::toRuntimeAction)
        val actionAccepted = BooleanArray(resolvedSlots.size)
        val rejectionCodes = IntArray(resolvedSlots.size)
        val actionIds = IntArray(resolvedSlots.size) { decodedActions[it].actionId }

        var applyNanos = 0L
        val runtimeResults =
            run {
                var results: List<HeadlessActionResult> = emptyList()
                applyNanos =
                    measureNanoTime {
                        results = runtime.applyActionsBatch(players, actionsForRuntime)
                    }
                results
            }

        runtimeResults.forEachIndexed { envIndex, result ->
            val decoded = decodedActions[envIndex]
            if (decoded.rejectionCode != FastRejectionCode.None) {
                actionAccepted[envIndex] = false
                rejectionCodes[envIndex] = decoded.rejectionCode.code
            } else {
                actionAccepted[envIndex] = result.actionApplied
                rejectionCodes[envIndex] = mapRejectionCode(result.rejectionReason).code
            }
        }

        var tickNanos = 0L
        tickNanos = measureNanoTime {
            runtime.tick(1)
        }

        var observeFlatNanos = 0L
        val batch =
            run {
                lateinit var observed: HeadlessTrainingFlatObservationBatchV1
                observeFlatNanos =
                    measureNanoTime {
                        observed = runtime.observeFlatBatch(players)
                    }
                observed
            }

        val flatObservations =
            FastFlatObsWriter.copyBatch(
                source = batch.values,
                envCount = resolvedSlots.size,
                featureCount = batch.featureCount,
                target = observationBuffer,
            )
        val terminated = BooleanArray(resolvedSlots.size)
        val truncated = BooleanArray(resolvedSlots.size)
        val terminalCodes = IntArray(resolvedSlots.size)
        val episodeTicks = IntArray(resolvedSlots.size)
        val episodeSteps = IntArray(resolvedSlots.size)
        val rewardRows = ArrayList<FloatArray>(resolvedSlots.size)
        val parityTraces = mutableListOf<FastParityTrace>()

        var projectionNanos = 0L
        projectionNanos =
            measureNanoTime {
                resolvedSlots.forEachIndexed { envIndex, slot ->
                    slot.episodeSteps += 1
                    val rowOffset = envIndex * batch.featureCount
                    val observationTick = flatObservations[rowOffset + 1].toInt()
                    val after = captureRewardSnapshot(slot)
                    val terminal =
                        FastTerminalStateEvaluator.infer(
                            playerHitpointsCurrent = after.playerHitpointsCurrent,
                            waveId = after.waveId,
                            remainingNpcs = after.remainingNpcCount,
                            observationTick = observationTick,
                            episodeStartTick = slot.episodeStartTick,
                            tickCap = tickCap,
                        )
                    val jadTrace = currentJadTrace(slot.player)
                    val rewardVector =
                        FastRewardFeatureWriter.encodeTransition(
                            before = rewardBefore[envIndex],
                            after = after,
                            actionName = decodedActions[envIndex].actionName,
                            actionAccepted = actionAccepted[envIndex],
                            terminal = terminal,
                            jadResolve = jadTrace?.toRewardResolveTrace(),
                        )
                    rewardRows += rewardVector
                    terminalCodes[envIndex] = terminal.terminalCode
                    terminated[envIndex] = terminal.terminated
                    truncated[envIndex] = terminal.truncated
                    episodeTicks[envIndex] = observationTick - checkNotNull(slot.episodeStartTick)
                    episodeSteps[envIndex] = slot.episodeSteps
                    if (emitParityTrace) {
                        parityTraces +=
                            buildParityTrace(
                                slot = slot,
                                actionName = decodedActions[envIndex].actionName,
                                actionAccepted = actionAccepted[envIndex],
                                rejectionCode = rejectionCodes[envIndex],
                                terminal = terminal,
                                damageDealt = rewardVector[FAST_REWARD_FEATURE_DAMAGE_DEALT_INDEX],
                                damageTaken = rewardVector[FAST_REWARD_FEATURE_DAMAGE_TAKEN_INDEX],
                            )
                    }
                }
            }

        val rewardFeatures =
            FastRewardFeatureWriter.packRows(
                rows = rewardRows,
                target = rewardFeatureBuffer,
            )

        return FastStepBatchResult(
            envCount = resolvedSlots.size,
            slotIndices = resolvedSlots.map { it.slotIndex }.toIntArray(),
            actionIds = actionIds,
            actionAccepted = actionAccepted,
            rejectionCodes = rejectionCodes,
            flatObservationFeatureCount = batch.featureCount,
            rewardFeatureCount = FIGHT_CAVES_FAST_REWARD_FEATURE_COUNT,
            flatObservations = flatObservations,
            rewardFeatures = rewardFeatures,
            terminalCodes = terminalCodes,
            terminated = terminated,
            truncated = truncated,
            episodeTicks = episodeTicks,
            episodeSteps = episodeSteps,
            parityTraces = parityTraces,
            metrics =
                FastStepMetrics(
                    applyActionsNanos = applyNanos,
                    tickNanos = tickNanos,
                    observeFlatNanos = observeFlatNanos,
                    projectionNanos = projectionNanos,
                    totalNanos = applyNanos + tickNanos + observeFlatNanos + projectionNanos,
                ),
        )
    }

    override fun close() {
        runtime.shutdown()
    }

    private fun resolveSlots(slotIndices: IntArray): List<FastRuntimeSlot> {
        require(slotIndices.isNotEmpty()) { "Fast batch requires at least one slot." }
        val seen = mutableSetOf<Int>()
        return slotIndices.map { slotIndex ->
            require(slotIndex in slots.indices) {
                "Fast batch slot index out of range: $slotIndex not in 0..${slots.lastIndex}."
            }
            require(seen.add(slotIndex)) {
                "Fast batch slot index must be unique, got duplicate $slotIndex."
            }
            slots[slotIndex]
        }
    }

    private fun ensureSlotReady(slot: FastRuntimeSlot) {
        check(slot.episodeStartTick != null) {
            "Fast kernel slot ${slot.slotIndex} has not been reset."
        }
    }

    private fun toRuntimeAction(decoded: FastDecodedAction): HeadlessAction =
        if (decoded.rejectionCode != FastRejectionCode.None) {
            HeadlessAction.Wait
        } else {
            when (decoded.actionId) {
                0 -> HeadlessAction.Wait
                1 -> {
                    val tile = checkNotNull(decoded.tile)
                    HeadlessAction.WalkToTile(Tile(tile.x, tile.y, tile.level))
                }
                2 -> HeadlessAction.AttackVisibleNpc(checkNotNull(decoded.visibleNpcIndex))
                3 ->
                    HeadlessAction.ToggleProtectionPrayer(
                        when (checkNotNull(decoded.prayer)) {
                            FastProtectionPrayer.ProtectFromMagic -> HeadlessProtectionPrayer.ProtectFromMagic
                            FastProtectionPrayer.ProtectFromMissiles -> HeadlessProtectionPrayer.ProtectFromMissiles
                            FastProtectionPrayer.ProtectFromMelee -> HeadlessProtectionPrayer.ProtectFromMelee
                            FastProtectionPrayer.None -> error("None is not a valid toggle prayer action.")
                        },
                    )
                4 -> HeadlessAction.EatShark
                5 -> HeadlessAction.DrinkPrayerPotion
                6 -> HeadlessAction.ToggleRun
                else -> HeadlessAction.Wait
            }
        }

    private fun mapRejectionCode(reason: HeadlessActionRejectReason?): FastRejectionCode =
        when (reason) {
            null -> FastRejectionCode.None
            HeadlessActionRejectReason.AlreadyActedThisTick -> FastRejectionCode.AlreadyActedThisTick
            HeadlessActionRejectReason.InvalidTargetIndex -> FastRejectionCode.InvalidTargetIndex
            HeadlessActionRejectReason.TargetNotVisible -> FastRejectionCode.TargetNotVisible
            HeadlessActionRejectReason.PlayerBusy -> FastRejectionCode.PlayerBusy
            HeadlessActionRejectReason.MissingConsumable -> FastRejectionCode.MissingConsumable
            HeadlessActionRejectReason.ConsumptionLocked -> FastRejectionCode.ConsumptionLocked
            HeadlessActionRejectReason.PrayerPointsDepleted -> FastRejectionCode.PrayerPointsDepleted
            HeadlessActionRejectReason.InsufficientRunEnergy -> FastRejectionCode.InsufficientRunEnergy
            HeadlessActionRejectReason.NoMovementRequired -> FastRejectionCode.NoMovementRequired
        }

    private fun captureRewardSnapshot(slot: FastRuntimeSlot): FastRewardSnapshot {
        val player = slot.player
        val npcs = fightCaveNpcs(player)
        val jad = npcs.firstOrNull { it.id == "tztok_jad" }
        return FastRewardSnapshot(
            playerHitpointsCurrent = player.levels.get(Skill.Constitution),
            totalNpcHitpoints = npcs.sumOf { npc -> npc.levels.get(Skill.Constitution).coerceAtLeast(0) },
            aliveNpcCount = npcs.count { npc -> !npc.contains("dead") && npc.levels.get(Skill.Constitution) > 0 },
            waveId = player[FIGHT_CAVE_WAVE_KEY, -1],
            remainingNpcCount = player[FIGHT_CAVE_REMAINING_KEY, 0],
            jadHitpointsCurrent = jad?.levels?.get(Skill.Constitution) ?: 0,
            jadAlive = jad != null && !jad.contains("dead") && jad.levels.get(Skill.Constitution) > 0,
            sharks = player.inventory.count("shark"),
            prayerPotionDoses = fightCavePrayerPotionDoseCount(player),
        )
    }

    private fun buildParityTrace(
        slot: FastRuntimeSlot,
        actionName: String,
        actionAccepted: Boolean,
        rejectionCode: Int,
        terminal: FastTerminalState,
        damageDealt: Float,
        damageTaken: Float,
    ): FastParityTrace {
        val player = slot.player
        val visibleTargets = runtime.visibleFightCaveNpcTargets(player)
        val visibleNpcType = Array(visibleTargets.size) { index -> visibleTargets[index].id }
        val visibleTargetOrder = IntArray(visibleTargets.size) { index -> visibleTargets[index].npcIndex }
        val visibleNpcHitpoints = IntArray(visibleTargets.size)
        val visibleNpcAlive = BooleanArray(visibleTargets.size)
        visibleTargets.forEachIndexed { index, target ->
            val npc = NPCs.indexed(target.npcIndex)
            val hitpoints = npc?.levels?.get(Skill.Constitution) ?: 0
            visibleNpcHitpoints[index] = hitpoints
            visibleNpcAlive[index] = npc != null && !npc.contains("dead") && hitpoints > 0
        }
        val jadTrace = currentJadTrace(player)
        val ammoItem = player.equipment[EquipSlot.Ammo.index]
        return FastParityTrace(
            tickIndex = GameLoop.tick - checkNotNull(slot.episodeStartTick),
            actionName = actionName,
            actionAccepted = actionAccepted,
            rejectionCode = rejectionCode,
            playerHitpoints = player.levels.get(Skill.Constitution),
            playerPrayerPoints = player.levels.get(Skill.Prayer),
            runEnabled = player.running,
            inventoryAmmo = if (ammoItem.isEmpty()) 0 else ammoItem.amount,
            inventorySharks = player.inventory.count("shark"),
            inventoryPrayerPotions = fightCavePrayerPotionDoseCount(player),
            waveId = player[FIGHT_CAVE_WAVE_KEY, -1],
            remainingNpcs = player[FIGHT_CAVE_REMAINING_KEY, 0],
            visibleTargetOrder = visibleTargetOrder,
            visibleNpcType = visibleNpcType,
            visibleNpcHitpoints = visibleNpcHitpoints,
            visibleNpcAlive = visibleNpcAlive,
            jadTelegraphState = jadTrace?.telegraphStateCode ?: 0,
            jadHitResolveOutcome = jadResolveOutcome(jadTrace),
            damageDealt = damageDealt,
            damageTaken = damageTaken,
            terminalCode = terminal.terminalCode,
        )
    }

    private fun currentJadTrace(player: Player): JadTelegraphTrace? =
        fightCaveNpcs(player).firstNotNullOfOrNull { npc ->
            npc.jadTelegraphTraceOrNull()
        }

    private fun jadResolveOutcome(trace: JadTelegraphTrace?): String =
        when {
            trace == null -> "none"
            trace.resolvedDamage < 0 -> "pending"
            trace.protectedAtPrayerCheck -> "protected"
            trace.resolvedDamage == 0 -> "zero_damage"
            else -> "hit"
        }

    private fun JadTelegraphTrace.toRewardResolveTrace(): FastJadResolveTrace =
        FastJadResolveTrace(
            committedAttackStyle = committedAttackStyle,
            protectedAtPrayerCheck = protectedAtPrayerCheck,
            resolvedDamage = resolvedDamage,
        )

    private fun fightCaveNpcs(player: Player): List<NPC> {
        val instance = player.instance()
        val source =
            if (instance != null) {
                buildList {
                    for (level in 0..3) {
                        addAll(NPCs.at(instance.toLevel(level)))
                    }
                }
            } else {
                NPCs.at(player.tile.regionLevel)
            }
        return source.filter { it.id in FIGHT_CAVE_NPC_IDS }
    }

    companion object {
        private val FIGHT_CAVE_NPC_IDS =
            setOf(
                "tz_kih",
                "tz_kih_spawn_point",
                "tz_kek",
                "tz_kek_spawn_point",
                "tz_kek_spawn",
                "tok_xil",
                "tok_xil_spawn_point",
                "yt_mej_kot",
                "yt_mej_kot_spawn_point",
                "ket_zek",
                "ket_zek_spawn_point",
                "tztok_jad",
                "yt_hur_kot",
            )

        @JvmStatic
        @JvmOverloads
        fun createKernel(
            slotCount: Int,
            tickCap: Int = 20_000,
            accountNamePrefix: String = "fast-kernel",
            settingsOverrides: Map<String, String> = emptyMap(),
        ): FastFightCavesKernelRuntime {
            require(slotCount > 0) { "Fast kernel slotCount must be > 0, got $slotCount." }
            require(tickCap > 0) { "Fast kernel tickCap must be > 0, got $tickCap." }

            val runtime =
                HeadlessMain.bootstrap(
                    loadContentScripts = true,
                    startWorld = false,
                    installShutdownHook = false,
                    settingsOverrides = defaultSettingsOverrides() + settingsOverrides,
                )
            val fightCave = runtime.loadedScripts.filterIsInstance<TzhaarFightCave>().single()
            val portableKernel = FastFightCavesKernel.scaffold(fightCave, runtime.configFiles)
            val accounts: AccountManager = get()
            val slots =
                List(slotCount) { index ->
                    FastRuntimeSlot(
                        slotIndex = index,
                        player = createPlayerSlot(accounts, "${accountNamePrefix}_$index", fightCave.centre),
                    )
                }
            return FastFightCavesKernelRuntime(
                runtime = runtime,
                portableKernel = portableKernel,
                slots = slots,
                tickCap = tickCap,
            )
        }

        private fun createPlayerSlot(accounts: AccountManager, name: String, tile: Tile): Player {
            val player = Player(tile = tile, accountName = name, passwordHash = "")
            check(accounts.setup(player, null, 0, viewport = true)) {
                "Failed to setup fast-kernel player '$name'."
            }
            player["creation"] = -1
            player["skip_level_up"] = true
            accounts.spawn(player, null)
            player.viewport?.loaded = true
            return player
        }

        private fun defaultSettingsOverrides(): Map<String, String> =
            mapOf(
                "storage.data" to "../data/",
                "storage.data.modified" to "../temp/data/headless-test-cache/modified.dat",
                "storage.cache.path" to "../data/cache/",
                "storage.wildcards" to "../temp/data/headless-test-cache/wildcards.txt",
                "storage.caching.path" to "../temp/data/headless-test-cache/",
                "storage.caching.active" to "false",
                "storage.players.path" to "../temp/data/test-saves/",
                "storage.players.logs" to "../temp/data/test-logs/",
                "storage.players.errors" to "../temp/data/test-errors/",
                "storage.autoSave.minutes" to "0",
                "events.shootingStars.enabled" to "false",
                "events.penguinHideAndSeek.enabled" to "false",
                "bots.count" to "0",
                "world.npcs.randomWalk" to "false",
                "storage.disabled" to "true",
                "headless.data.allowlist.path" to "config/headless_data_allowlist.toml",
                "headless.scripts.allowlist.path" to "config/headless_scripts.txt",
                "headless.manifest.path" to "config/headless_manifest.toml",
            )
    }
}
