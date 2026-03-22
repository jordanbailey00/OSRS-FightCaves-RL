package headless.fast

import content.area.karamja.tzhaar_city.TzhaarFightCave
import content.entity.player.effect.energy.MAX_RUN_ENERGY
import sim.engine.data.ConfigFiles
import sim.engine.entity.character.player.skill.Skill

data class FastFightCavesKernelDescriptor(
    val contract: FastKernelSurfaceContract,
    val actionSchema: FastActionSchema,
    val flatObservationSchemaId: String,
    val flatObservationSchemaVersion: Int,
    val flatObservationDType: String,
    val flatObservationFeatureCount: Int,
    val flatObservationMaxVisibleNpcs: Int,
    val rewardFeatureSchemaId: String,
    val rewardFeatureSchemaVersion: Int,
    val rewardFeatureCount: Int,
    val waveCount: Int,
    val defaultPlayerSpawn: FastTile,
    val jadTelegraphSpec: FastJadTelegraphSpec,
)

class FastFightCavesKernel private constructor(
    val contract: FastKernelSurfaceContract,
    val actionSchema: FastActionSchema,
    val waveRegistry: FastWaveRegistry,
    val episodeInitializer: FastEpisodeInitializer,
    val defaultPlayerSpawn: FastTile,
    val jadTelegraphSpec: FastJadTelegraphSpec,
) {

    fun describe(): FastFightCavesKernelDescriptor =
        FastFightCavesKernelDescriptor(
            contract = contract,
            actionSchema = actionSchema,
            flatObservationSchemaId = FIGHT_CAVES_FAST_FLAT_OBSERVATION_SCHEMA_ID,
            flatObservationSchemaVersion = FIGHT_CAVES_FAST_FLAT_OBSERVATION_SCHEMA_VERSION,
            flatObservationDType = FIGHT_CAVES_FAST_FLAT_OBSERVATION_DTYPE,
            flatObservationFeatureCount = FIGHT_CAVES_FAST_FLAT_OBSERVATION_FEATURE_COUNT,
            flatObservationMaxVisibleNpcs = FIGHT_CAVES_FAST_FLAT_OBSERVATION_MAX_VISIBLE_NPCS,
            rewardFeatureSchemaId = FIGHT_CAVES_FAST_REWARD_FEATURE_SCHEMA_ID,
            rewardFeatureSchemaVersion = FIGHT_CAVES_FAST_REWARD_FEATURE_SCHEMA_VERSION,
            rewardFeatureCount = FIGHT_CAVES_FAST_REWARD_FEATURE_COUNT,
            waveCount = waveRegistry.waveCount,
            defaultPlayerSpawn = defaultPlayerSpawn,
            jadTelegraphSpec = jadTelegraphSpec,
        )

    fun initializeScaffoldSlot(
        slotIndex: Int,
        config: FastEpisodeConfig,
    ): FastSlotState {
        require(slotIndex >= 0) { "Fast kernel slot index must be >= 0, got $slotIndex." }

        val template = episodeInitializer.buildTemplate(config)
        val waveDefinition = waveRegistry.definition(template.initialWaveId)
        val resolvedRotation = template.rotationResolution.resolvedRotation

        return FastSlotState(
            slotIndex = slotIndex,
            episodeSeed = config.seed,
            tick = 0,
            player =
                FastPlayerState(
                    tile = defaultPlayerSpawn,
                    hitpointsCurrent = FIGHT_CAVE_FIXED_LEVELS.getValue(Skill.Constitution),
                    hitpointsMax = FIGHT_CAVE_FIXED_LEVELS.getValue(Skill.Constitution),
                    prayerCurrent = FIGHT_CAVE_FIXED_LEVELS.getValue(Skill.Prayer),
                    prayerMax = FIGHT_CAVE_FIXED_LEVELS.getValue(Skill.Prayer),
                    runEnergy = MAX_RUN_ENERGY,
                    runEnergyMax = MAX_RUN_ENERGY,
                    running = true,
                    activeProtectionPrayer = FastProtectionPrayer.None,
                    inventory =
                        FastInventoryState(
                            ammo = config.ammo,
                            sharks = config.sharks,
                            prayerPotionDoses = config.prayerPotions * 4,
                        ),
                    delays = FastDelayState(),
                ),
            wave =
                FastWaveState(
                    currentWave = template.initialWaveId,
                    resolvedRotation = resolvedRotation,
                    remainingNpcCount = template.initialRemainingNpcCount,
                    spawnDirections =
                        if (resolvedRotation == null) {
                            emptyList()
                        } else {
                            waveDefinition.rotation(resolvedRotation).spawnDirections
                        },
                    configuredNpcIds = waveDefinition.npcIds,
                ),
            visibleNpcs = emptyList(),
        )
    }

    companion object {
        fun scaffold(
            fightCave: TzhaarFightCave,
            configFiles: ConfigFiles,
        ): FastFightCavesKernel {
            fightCave.ensureWavesLoaded(configFiles)
            val waveRegistry = FastWaveRegistry.fromFightCaveWaves(fightCave.waves)
            return FastFightCavesKernel(
                contract = FastKernelSurfaceContract(),
                actionSchema = FIGHT_CAVES_FAST_ACTION_SCHEMA,
                waveRegistry = waveRegistry,
                episodeInitializer = FastEpisodeInitializer(waveRegistry),
                defaultPlayerSpawn =
                    FastTile(
                        x = fightCave.centre.x,
                        y = fightCave.centre.y,
                        level = fightCave.centre.level,
                    ),
                jadTelegraphSpec = FAST_JAD_TELEGRAPH_SPEC,
            )
        }
    }
}
