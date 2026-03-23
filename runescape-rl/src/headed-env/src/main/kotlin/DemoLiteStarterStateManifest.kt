import headless.fast.FIGHT_CAVE_DEFAULT_EQUIPMENT_TEMPLATE
import headless.fast.FIGHT_CAVE_DEFAULT_INVENTORY_TEMPLATE
import headless.fast.FIGHT_CAVE_FIXED_LEVELS
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.inv.equipment
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.network.login.protocol.visual.update.player.EquipSlot

data class DemoLiteItemManifestEntry(
    val itemId: String,
    val amount: Int,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "item_id" to itemId,
            "amount" to amount,
        )
}

data class DemoLiteSkillManifestEntry(
    val level: Int,
    val experienceTenths: Int,
    val blocked: Boolean,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "level" to level,
            "experience_tenths" to experienceTenths,
            "blocked" to blocked,
        )
}

data class DemoLiteStarterStateManifest(
    val schemaId: String = "demo_lite_starter_state_manifest_v1",
    val canonicalSource: String = "RL/RLspec.md#7.1.1 Episode-start state contract",
    val derivedFromSharedInitializer: Boolean,
    val sharedInitializerPath: String,
    val startWaveRequested: Int,
    val episodeSeed: Long,
    val instanceId: Int,
    val resetStatePlayerTile: HeadlessObservationTile,
    val observedPlayerTile: HeadlessObservationTile,
    val observedWave: Int,
    val observedRotation: Int,
    val observedRemaining: Int,
    val equipment: LinkedHashMap<String, DemoLiteItemManifestEntry?>,
    val inventory: LinkedHashMap<String, DemoLiteItemManifestEntry>,
    val skills: LinkedHashMap<String, DemoLiteSkillManifestEntry>,
    val hitpointsDisplay: Int,
    val constitutionCurrent: Int,
    val constitutionMax: Int,
    val prayerCurrent: Int,
    val prayerMax: Int,
    val runEnergy: Int,
    val runEnergyMax: Int,
    val runEnergyPercent: Int,
    val running: Boolean,
    val movementMode: String,
    val skipLevelUp: Boolean,
    val noXpGainConfigured: Boolean,
    val blockedSkills: List<String>,
    val ammoId: String?,
    val ammoCount: Int,
    val sharkCount: Int,
    val prayerPotionDoseCount: Int,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "schema_id" to schemaId,
            "canonical_source" to canonicalSource,
            "derived_from_shared_initializer" to derivedFromSharedInitializer,
            "shared_initializer_path" to sharedInitializerPath,
            "start_wave_requested" to startWaveRequested,
            "episode_seed" to episodeSeed,
            "instance_id" to instanceId,
            "reset_state_player_tile" to resetStatePlayerTile.toOrderedMap(),
            "observed_player_tile" to observedPlayerTile.toOrderedMap(),
            "observed_wave" to observedWave,
            "observed_rotation" to observedRotation,
            "observed_remaining" to observedRemaining,
            "equipment" to equipment.mapValues { (_, entry) -> entry?.toOrderedMap() },
            "inventory" to inventory.mapValues { (_, entry) -> entry.toOrderedMap() },
            "skills" to skills.mapValues { (_, entry) -> entry.toOrderedMap() },
            "hitpoints_display" to hitpointsDisplay,
            "constitution_current" to constitutionCurrent,
            "constitution_max" to constitutionMax,
            "prayer_current" to prayerCurrent,
            "prayer_max" to prayerMax,
            "run_energy" to runEnergy,
            "run_energy_max" to runEnergyMax,
            "run_energy_percent" to runEnergyPercent,
            "running" to running,
            "movement_mode" to movementMode,
            "skip_level_up" to skipLevelUp,
            "no_xp_gain_configured" to noXpGainConfigured,
            "blocked_skills" to blockedSkills,
            "ammo_id" to (ammoId ?: ""),
            "ammo_count" to ammoCount,
            "shark_count" to sharkCount,
            "prayer_potion_dose_count" to prayerPotionDoseCount,
        )

    companion object {
        fun capture(context: DemoLiteEpisodeContext, observation: HeadlessObservationV1): DemoLiteStarterStateManifest {
            val player = context.player
            val equipment =
                linkedMapOf(
                    "hat" to player.equipmentEntryFor(EquipSlot.Hat.index),
                    "weapon" to player.equipmentEntryFor(EquipSlot.Weapon.index),
                    "chest" to player.equipmentEntryFor(EquipSlot.Chest.index),
                    "legs" to player.equipmentEntryFor(EquipSlot.Legs.index),
                    "hands" to player.equipmentEntryFor(EquipSlot.Hands.index),
                    "feet" to player.equipmentEntryFor(EquipSlot.Feet.index),
                    "ammo" to player.equipmentEntryFor(EquipSlot.Ammo.index),
                    "neck" to player.equipmentEntryFor(EquipSlot.Amulet.index),
                )
            val inventory =
                linkedMapOf<String, DemoLiteItemManifestEntry>().apply {
                    for (entry in FIGHT_CAVE_DEFAULT_INVENTORY_TEMPLATE) {
                        this[entry.itemId] = DemoLiteItemManifestEntry(entry.itemId, player.inventory.count(entry.itemId))
                    }
                }
            val skills =
                linkedMapOf<String, DemoLiteSkillManifestEntry>().apply {
                    for (skill in Skill.all) {
                        put(
                            skill.name.lowercase(),
                            DemoLiteSkillManifestEntry(
                                level = player.levels.get(skill),
                                experienceTenths = player.experience.direct(skill),
                                blocked = player.experience.blocked(skill),
                            ),
                        )
                    }
                }
            val ammoEntry = equipment.getValue("ammo")
            return DemoLiteStarterStateManifest(
                derivedFromSharedInitializer = true,
                sharedInitializerPath =
                    "FightCaveEpisodeInitializer.reset -> FIGHT_CAVE_DEFAULT_EQUIPMENT_TEMPLATE / " +
                        "FIGHT_CAVE_DEFAULT_INVENTORY_TEMPLATE / FIGHT_CAVE_FIXED_LEVELS",
                startWaveRequested = context.episodeConfig.startWave,
                episodeSeed = context.episodeConfig.seed,
                instanceId = context.episodeState.instanceId,
                resetStatePlayerTile = HeadlessObservationTile.from(context.episodeState.playerTile),
                observedPlayerTile = observation.player.tile,
                observedWave = observation.wave.wave,
                observedRotation = observation.wave.rotation,
                observedRemaining = observation.wave.remaining,
                equipment = equipment,
                inventory = inventory,
                skills = skills,
                hitpointsDisplay = player.levels.get(Skill.Constitution) / 10,
                constitutionCurrent = player.levels.get(Skill.Constitution),
                constitutionMax = player.levels.getMax(Skill.Constitution),
                prayerCurrent = player.levels.get(Skill.Prayer),
                prayerMax = player.levels.getMax(Skill.Prayer),
                runEnergy = observation.player.runEnergy,
                runEnergyMax = observation.player.runEnergyMax,
                runEnergyPercent = observation.player.runEnergyPercent,
                running = observation.player.running,
                movementMode = player["movement_temp", "walk"],
                skipLevelUp = player["skip_level_up", false],
                noXpGainConfigured = Skill.all.all { skill -> player.experience.blocked(skill) },
                blockedSkills = Skill.all.filter { skill -> player.experience.blocked(skill) }.map { it.name.lowercase() },
                ammoId = ammoEntry?.itemId,
                ammoCount = ammoEntry?.amount ?: 0,
                sharkCount = observation.player.consumables.sharkCount,
                prayerPotionDoseCount = observation.player.consumables.prayerPotionDoseCount,
            )
        }

        fun expectedEquipment(config: FightCaveEpisodeConfig): LinkedHashMap<String, DemoLiteItemManifestEntry?> =
            linkedMapOf(
                "hat" to templateEntry("coif"),
                "weapon" to templateEntry("rune_crossbow"),
                "chest" to templateEntry("black_dragonhide_body"),
                "legs" to templateEntry("black_dragonhide_chaps"),
                "hands" to templateEntry("black_dragonhide_vambraces"),
                "feet" to templateEntry("snakeskin_boots"),
                "ammo" to DemoLiteItemManifestEntry("adamant_bolts", config.ammo),
                "neck" to null,
            )

        fun expectedInventory(config: FightCaveEpisodeConfig): LinkedHashMap<String, DemoLiteItemManifestEntry> =
            linkedMapOf(
                "prayer_potion_4" to DemoLiteItemManifestEntry("prayer_potion_4", config.prayerPotions),
                "shark" to DemoLiteItemManifestEntry("shark", config.sharks),
            )

        fun expectedSkills(): LinkedHashMap<String, Int> =
            linkedMapOf<String, Int>().apply {
                for (skill in Skill.all) {
                    put(skill.name.lowercase(), FIGHT_CAVE_FIXED_LEVELS[skill] ?: 1)
                }
            }

        private fun templateEntry(itemId: String): DemoLiteItemManifestEntry {
            val template = checkNotNull(FIGHT_CAVE_DEFAULT_EQUIPMENT_TEMPLATE.firstOrNull { it.itemId == itemId }) {
                "Missing fight cave equipment template for '$itemId'."
            }
            return DemoLiteItemManifestEntry(template.itemId, template.amount)
        }

        private fun Player.equipmentEntryFor(slot: Int): DemoLiteItemManifestEntry? {
            val item = equipment[slot]
            if (item.id.isBlank() || item.amount <= 0) {
                return null
            }
            return DemoLiteItemManifestEntry(item.id, item.amount)
        }
    }
}
