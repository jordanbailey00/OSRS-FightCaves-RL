package headless.fast

data class FastTile(
    val x: Int,
    val y: Int,
    val level: Int,
)

enum class FastProtectionPrayer {
    None,
    ProtectFromMagic,
    ProtectFromMissiles,
    ProtectFromMelee,
}

data class FastInventoryState(
    val ammo: Int,
    val sharks: Int,
    val prayerPotionDoses: Int,
)

data class FastDelayState(
    val actionDelayTicks: Int = 0,
    val foodDelayTicks: Int = 0,
    val drinkDelayTicks: Int = 0,
    val comboDelayTicks: Int = 0,
    val movementDelayTicks: Int = 0,
    val stunnedTicks: Int = 0,
)

data class FastPlayerState(
    val tile: FastTile,
    val hitpointsCurrent: Int,
    val hitpointsMax: Int,
    val prayerCurrent: Int,
    val prayerMax: Int,
    val runEnergy: Int,
    val runEnergyMax: Int,
    val running: Boolean,
    val activeProtectionPrayer: FastProtectionPrayer,
    val inventory: FastInventoryState,
    val delays: FastDelayState,
)

data class FastNpcState(
    val visibleSlot: Int,
    val npcIndex: Int,
    val npcId: String,
    val tile: FastTile,
    val hitpointsCurrent: Int? = null,
    val hitpointsMax: Int? = null,
    val hidden: Boolean = false,
    val alive: Boolean = true,
    val underAttack: Boolean = false,
    val jadTelegraphState: FastJadTelegraphState = FastJadTelegraphState.Idle,
)

data class FastWaveState(
    val currentWave: Int,
    val resolvedRotation: Int?,
    val remainingNpcCount: Int,
    val spawnDirections: List<FastSpawnDirection>,
    val configuredNpcIds: List<String>,
)

data class FastSlotState(
    val slotIndex: Int,
    val episodeSeed: Long,
    val tick: Int,
    val player: FastPlayerState,
    val wave: FastWaveState,
    val visibleNpcs: List<FastNpcState>,
    val terminalCode: Int = 0,
    val terminalReason: String? = null,
)
