package headless.fast

const val FIGHT_CAVES_FAST_KERNEL_SURFACE_CONTRACT_ID = "fight_caves_fast_kernel_surface_v1"
const val FIGHT_CAVES_FAST_KERNEL_SURFACE_CONTRACT_VERSION = 1
const val FIGHT_CAVES_FAST_KERNEL_SURFACE_COMPATIBILITY_POLICY =
    "independent_of_v1_bridge_surface"

data class FastKernelSurfaceContract(
    val contractId: String = FIGHT_CAVES_FAST_KERNEL_SURFACE_CONTRACT_ID,
    val version: Int = FIGHT_CAVES_FAST_KERNEL_SURFACE_CONTRACT_VERSION,
    val compatibilityPolicy: String = FIGHT_CAVES_FAST_KERNEL_SURFACE_COMPATIBILITY_POLICY,
    val sharedActionSchemaId: String = "headless_action_v1",
    val sharedActionSchemaVersion: Int = 1,
    val sharedEpisodeStartContractId: String = "fight_cave_episode_start_v1",
    val sharedEpisodeStartContractVersion: Int = 1,
    val sharedFlatObservationSchemaId: String = "headless_training_flat_observation_v1",
    val sharedFlatObservationSchemaVersion: Int = 1,
    val sharedTerminalCodeSchemaId: String = "fight_caves_v2_terminal_codes_v1",
    val sharedTerminalCodeSchemaVersion: Int = 1,
    val sharedRewardFeatureSchemaId: String = "fight_caves_v2_reward_features_v1",
    val sharedRewardFeatureSchemaVersion: Int = 1,
    val sharedParityTraceSchemaId: String = "fight_caves_mechanics_parity_trace_v1",
    val sharedParityTraceSchemaVersion: Int = 1,
    val portabilityGoal: String = "future_native_kernel_portable_state_and_api",
)

data class FastActionDescriptor(
    val actionId: Int,
    val actionName: String,
    val parameterNames: List<String>,
)

data class FastActionSchema(
    val packedWordCount: Int,
    val packedDType: String,
    val descriptors: List<FastActionDescriptor>,
) {
    fun descriptor(actionId: Int): FastActionDescriptor =
        descriptors.firstOrNull { it.actionId == actionId }
            ?: error("Unsupported fast action id $actionId.")
}

val FIGHT_CAVES_FAST_ACTION_SCHEMA =
    FastActionSchema(
        packedWordCount = 4,
        packedDType = "int32",
        descriptors =
            listOf(
                FastActionDescriptor(0, "wait", emptyList()),
                FastActionDescriptor(1, "walk_to_tile", listOf("tile_x", "tile_y", "tile_level")),
                FastActionDescriptor(2, "attack_visible_npc", listOf("visible_npc_index")),
                FastActionDescriptor(3, "toggle_protection_prayer", listOf("prayer_id")),
                FastActionDescriptor(4, "eat_shark", emptyList()),
                FastActionDescriptor(5, "drink_prayer_potion", emptyList()),
                FastActionDescriptor(6, "toggle_run", emptyList()),
            ),
    )

const val FIGHT_CAVES_FAST_FLAT_OBSERVATION_SCHEMA_ID = "headless_training_flat_observation_v1"
const val FIGHT_CAVES_FAST_FLAT_OBSERVATION_SCHEMA_VERSION = 1
const val FIGHT_CAVES_FAST_FLAT_OBSERVATION_DTYPE = "float32"
const val FIGHT_CAVES_FAST_FLAT_OBSERVATION_BASE_FIELD_COUNT = 30
const val FIGHT_CAVES_FAST_FLAT_OBSERVATION_NPC_FIELD_COUNT = 13
const val FIGHT_CAVES_FAST_FLAT_OBSERVATION_MAX_VISIBLE_NPCS = 8
const val FIGHT_CAVES_FAST_FLAT_OBSERVATION_FEATURE_COUNT =
    FIGHT_CAVES_FAST_FLAT_OBSERVATION_BASE_FIELD_COUNT +
        (FIGHT_CAVES_FAST_FLAT_OBSERVATION_NPC_FIELD_COUNT * FIGHT_CAVES_FAST_FLAT_OBSERVATION_MAX_VISIBLE_NPCS)

const val FIGHT_CAVES_FAST_REWARD_FEATURE_SCHEMA_ID = "fight_caves_v2_reward_features_v1"
const val FIGHT_CAVES_FAST_REWARD_FEATURE_SCHEMA_VERSION = 1
const val FIGHT_CAVES_FAST_REWARD_FEATURE_COUNT = 16

val FAST_PROTECTION_PRAYER_IDS =
    listOf(
        "protect_from_magic",
        "protect_from_missiles",
        "protect_from_melee",
    )

data class FastEpisodeConfig(
    val seed: Long,
    val startWave: Int = 1,
    val ammo: Int = 1000,
    val prayerPotions: Int = 8,
    val sharks: Int = 20,
)

data class FastSkillLevelTemplate(
    val skillName: String,
    val level: Int,
)

data class FastEquipmentStack(
    val slotName: String,
    val itemId: String,
    val amount: Int,
)

data class FastInventoryStack(
    val itemId: String,
    val amount: Int,
)

data class FastRotationResolution(
    val resolvedRotation: Int?,
    val resolutionPolicy: String,
)

data class FastEpisodeTemplate(
    val config: FastEpisodeConfig,
    val fixedLevels: List<FastSkillLevelTemplate>,
    val equipment: List<FastEquipmentStack>,
    val inventory: List<FastInventoryStack>,
    val initialWaveId: Int,
    val initialRemainingNpcCount: Int,
    val rotationResolution: FastRotationResolution,
)
