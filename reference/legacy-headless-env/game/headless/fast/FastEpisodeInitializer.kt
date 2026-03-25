package headless.fast

class FastEpisodeInitializer(
    private val waveRegistry: FastWaveRegistry,
) {

    fun buildTemplate(config: FastEpisodeConfig): FastEpisodeTemplate {
        require(config.startWave in 1..63) {
            "Fight Caves fast start wave must be in range 1..63, got ${config.startWave}."
        }
        require(config.ammo > 0) { "Fast kernel ammo amount must be > 0, got ${config.ammo}." }
        require(config.prayerPotions >= 0) {
            "Fast kernel prayer potion count cannot be negative, got ${config.prayerPotions}."
        }
        require(config.sharks >= 0) {
            "Fast kernel shark count cannot be negative, got ${config.sharks}."
        }

        val wave = waveRegistry.definition(config.startWave)
        val rotationResolution =
            if (config.startWave == 1) {
                FastRotationResolution(
                    resolvedRotation = null,
                    resolutionPolicy = "defer_to_seeded_wave_one_sampler",
                )
            } else {
                FastRotationResolution(
                    resolvedRotation = 1,
                    resolutionPolicy = "mirror_current_headless_start_wave_gt_1_default",
                )
            }

        return FastEpisodeTemplate(
            config = config,
            fixedLevels =
                FIGHT_CAVE_FIXED_LEVELS.entries.map { entry ->
                    FastSkillLevelTemplate(
                        skillName = entry.key.name,
                        level = entry.value,
                    )
                },
            equipment =
                FIGHT_CAVE_DEFAULT_EQUIPMENT_TEMPLATE.map { entry ->
                    FastEquipmentStack(
                        slotName = entry.slot.name.lowercase(),
                        itemId = entry.itemId,
                        amount = if (entry.slot == FightCaveEquipmentSlot.Ammo) config.ammo else entry.amount,
                    )
                },
            inventory =
                FIGHT_CAVE_DEFAULT_INVENTORY_TEMPLATE.map { entry ->
                    FastInventoryStack(
                        itemId = entry.itemId,
                        amount =
                            when (entry.itemId) {
                                "prayer_potion_4" -> config.prayerPotions
                                "shark" -> config.sharks
                                else -> entry.amount
                            },
                    )
                },
            initialWaveId = wave.waveId,
            initialRemainingNpcCount = wave.remainingNpcCount,
            rotationResolution = rotationResolution,
        )
    }
}
