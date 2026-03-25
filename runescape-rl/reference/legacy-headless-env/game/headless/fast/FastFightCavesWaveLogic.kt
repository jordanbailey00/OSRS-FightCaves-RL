package headless.fast

import content.area.karamja.tzhaar_city.TzhaarFightCaveWaves
import sim.types.Direction

enum class FastSpawnDirection {
    NorthWest,
    SouthEast,
    South,
    SouthWest,
    None,
}

data class FastWaveRotation(
    val rotationId: Int,
    val spawnDirections: List<FastSpawnDirection>,
)

data class FastWaveDefinition(
    val waveId: Int,
    val npcIds: List<String>,
    val remainingNpcCount: Int,
    val rotations: List<FastWaveRotation>,
) {
    fun rotation(rotationId: Int): FastWaveRotation =
        rotations.firstOrNull { it.rotationId == rotationId }
            ?: error("Unsupported fight cave rotation $rotationId for wave $waveId.")
}

data class FastWaveRegistry(
    val definitions: List<FastWaveDefinition>,
) {
    val waveCount: Int
        get() = definitions.size

    fun definition(waveId: Int): FastWaveDefinition =
        definitions.firstOrNull { it.waveId == waveId }
            ?: error("Unsupported fight cave wave $waveId.")

    companion object {
        fun fromFightCaveWaves(waves: TzhaarFightCaveWaves): FastWaveRegistry =
            FastWaveRegistry(
                definitions =
                    (1..63).map { waveId ->
                        val npcIds = waves.npcs(waveId)
                        FastWaveDefinition(
                            waveId = waveId,
                            npcIds = npcIds,
                            remainingNpcCount = fightCaveRemainingNpcCount(npcIds),
                            rotations =
                                (1..15).map { rotationId ->
                                    FastWaveRotation(
                                        rotationId = rotationId,
                                        spawnDirections = waves.spawns(waveId, rotationId).map(::toFastSpawnDirection),
                                    )
                                },
                        )
                    },
            )
    }
}

private fun toFastSpawnDirection(direction: Direction): FastSpawnDirection =
    when (direction) {
        Direction.NORTH_WEST -> FastSpawnDirection.NorthWest
        Direction.SOUTH_EAST -> FastSpawnDirection.SouthEast
        Direction.SOUTH -> FastSpawnDirection.South
        Direction.SOUTH_WEST -> FastSpawnDirection.SouthWest
        Direction.NONE -> FastSpawnDirection.None
        else -> error("Unsupported fight cave spawn direction $direction for fast scaffold.")
    }
