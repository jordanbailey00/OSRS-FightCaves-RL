package headless.fast

import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.inv.inventory

const val FIGHT_CAVE_EPISODE_SEED_KEY = "episode_seed"
const val FIGHT_CAVE_WAVE_KEY = "fight_cave_wave"
const val FIGHT_CAVE_ROTATION_KEY = "fight_cave_rotation"
const val FIGHT_CAVE_REMAINING_KEY = "fight_cave_remaining"

enum class FightCaveEquipmentSlot {
    Hat,
    Weapon,
    Chest,
    Legs,
    Hands,
    Feet,
    Ammo,
}

data class FightCaveEquipmentTemplate(
    val slot: FightCaveEquipmentSlot,
    val itemId: String,
    val amount: Int = 1,
)

data class FightCaveInventoryTemplate(
    val itemId: String,
    val amount: Int,
)

val FIGHT_CAVE_RESET_CLOCKS =
    listOf(
        "delay",
        "movement_delay",
        "food_delay",
        "drink_delay",
        "combo_delay",
        "fight_cave_cooldown",
    )

val FIGHT_CAVE_FIXED_LEVELS =
    mapOf(
        Skill.Attack to 1,
        Skill.Strength to 1,
        Skill.Defence to 70,
        Skill.Constitution to 700,
        Skill.Ranged to 70,
        Skill.Prayer to 43,
        Skill.Magic to 1,
    )

val FIGHT_CAVE_RESET_VARIABLES =
    listOf(
        FIGHT_CAVE_WAVE_KEY,
        FIGHT_CAVE_ROTATION_KEY,
        FIGHT_CAVE_REMAINING_KEY,
        "fight_cave_start_time",
        "healed",
    )

val FIGHT_CAVE_DEFAULT_EQUIPMENT_TEMPLATE =
    listOf(
        FightCaveEquipmentTemplate(FightCaveEquipmentSlot.Hat, "coif"),
        FightCaveEquipmentTemplate(FightCaveEquipmentSlot.Weapon, "rune_crossbow"),
        FightCaveEquipmentTemplate(FightCaveEquipmentSlot.Chest, "black_dragonhide_body"),
        FightCaveEquipmentTemplate(FightCaveEquipmentSlot.Legs, "black_dragonhide_chaps"),
        FightCaveEquipmentTemplate(FightCaveEquipmentSlot.Hands, "black_dragonhide_vambraces"),
        FightCaveEquipmentTemplate(FightCaveEquipmentSlot.Feet, "snakeskin_boots"),
        FightCaveEquipmentTemplate(FightCaveEquipmentSlot.Ammo, "adamant_bolts"),
    )

val FIGHT_CAVE_DEFAULT_INVENTORY_TEMPLATE =
    listOf(
        FightCaveInventoryTemplate("prayer_potion_4", 8),
        FightCaveInventoryTemplate("shark", 20),
    )

fun fightCaveRemainingNpcCount(npcIds: List<String>): Int =
    npcIds.sumOf { if (it == "tz_kek" || it == "tz_kek_spawn_point") 2 else 1 }

fun fightCavePrayerPotionDoseCount(player: Player): Int {
    var doses = 0
    for (slot in player.inventory.indices) {
        val item = player.inventory[slot]
        doses +=
            when (item.id) {
                "prayer_potion_4" -> 4 * item.amount
                "prayer_potion_3" -> 3 * item.amount
                "prayer_potion_2" -> 2 * item.amount
                "prayer_potion_1" -> item.amount
                else -> 0
            }
    }
    return doses
}
