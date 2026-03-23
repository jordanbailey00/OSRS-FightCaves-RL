package content.skill.mining

import content.activity.shooting_star.ShootingStarHandler
import content.entity.player.bank.bank
import net.pearx.kasechange.toLowerSpaceCase
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.variable.remaining
import sim.engine.client.variable.start
import sim.engine.client.variable.stop
import sim.engine.data.definition.EnumDefinitions
import sim.engine.entity.World
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.chat.inventoryFull
import sim.engine.entity.character.player.equip.equipped
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level.has
import sim.engine.entity.character.player.skill.level.Level.hasRequirementsToUse
import sim.engine.entity.character.player.skill.level.Level.success
import sim.engine.entity.item.Item
import sim.engine.entity.obj.GameObject
import sim.engine.entity.obj.GameObjects
import sim.engine.inv.add
import sim.engine.inv.inventory
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.types.random

class Mining : Script {

    val gems = setOf(
        "uncut_sapphire",
        "uncut_emerald",
        "uncut_ruby",
        "uncut_diamond",
    )

    val gemRocks = setOf(
        "uncut_opal",
        "uncut_jade",
        "uncut_red_topaz",
        "uncut_sapphire",
        "uncut_emerald",
        "uncut_ruby",
        "uncut_diamond",
    )

    val sandstone = setOf(
        "sandstone_10kg",
        "sandstone_5kg",
        "sandstone_2kg",
        "sandstone_1kg",
    )

    val granite = setOf(
        "granite_5kg",
        "granite_2kg",
        "granite_500g",
    )

    init {
        objectOperate("Mine") { (target) ->
            if (target.id.startsWith("depleted")) {
                message("There is currently no ore available in this rock.")
                return@objectOperate
            }
            softTimers.start("mining")
            var first = true
            while (true) {
                if (!GameObjects.contains(target)) {
                    break
                }

                if (inventory.isFull()) {
                    message("Your inventory is too full to hold any more ore.")
                    break
                }

                val ore = EnumDefinitions.stringOrNull("mining_ores", target.id) ?: break
                val stringId = target.def(this).stringId
                val level = if (stringId.startsWith("crashed_star_tier_")) {
                    stringId.removePrefix("crashed_star_tier_").toInt() * 10
                } else {
                    EnumDefinitions.int("mining_level", ore)
                }
                if (!has(Skill.Mining, level, true)) {
                    break
                }

                val pickaxe = Pickaxe.best(this)
                if (!hasRequirements(this, pickaxe, true) || pickaxe == null) {
                    break
                }

                val delay = if (pickaxe.id == "dragon_pickaxe" && random.nextInt(6) == 0) 2 else pickaxe.def["mining_delay", 8]
                if (first) {
                    message("You swing your pickaxe at the rock.", ChatType.Filter)
                    first = false
                }
                val remaining = remaining("action_delay")
                if (remaining < 0) {
                    face(target)
                    anim("${pickaxe.id}_swing_low")
                    start("action_delay", delay)
                    pause(delay)
                } else if (remaining > 0) {
                    pause(delay)
                }
                if (!GameObjects.contains(target)) {
                    break
                }
                if (EnumDefinitions.contains("mining_gems", target.id)) {
                    val glory = equipped(EquipSlot.Amulet).id.startsWith("amulet_of_glory_")
                    if (success(levels.get(Skill.Mining), if (glory) 3..3 else 1..1)) {
                        addOre(this, gems.random())
                        continue
                    }
                }
                var ores = mutableListOf<String>()
                when {
                    target.id == "rune_essence_rocks" -> {
                        if (World.members && has(Skill.Mining, 30)) {
                            ores.add("pure_essence")
                        } else {
                            ores.add("rune_essence")
                        }
                    }
                    ore == "granite_500g" -> ores.addAll(granite)
                    ore == "sandstone_1kg" -> ores.addAll(sandstone)
                    ore == "uncut_opal" -> ores.addAll(gemRocks)
                    else -> ores.add(ore)
                }
                for (item in ores) {
                    val chanceMin = EnumDefinitions.int("mining_chance_min", item)
                    val chanceMax = EnumDefinitions.int("mining_chance_max", item)
                    if (success(levels.get(Skill.Mining), chanceMin..chanceMax)) {
                        val xp = EnumDefinitions.int("mining_xp", item) / 10.0
                        experience.add(Skill.Mining, xp)
                        ShootingStarHandler.extraOreHandler(this, item, xp)
                        if (!addOre(this, item) || deplete(target, EnumDefinitions.int("mining_life", item))) {
                            clearAnim()
                            break
                        }
                    }
                }
                stop("action_delay")
            }
            softTimers.stop("mining")
        }

        objectApproach("Prospect") { (target) ->
            approachRange(1)
            arriveDelay()
            if (target.id.startsWith("depleted")) {
                message("There is currently no ore available in this rock.")
                return@objectApproach
            }
            if (queue.contains("prospect")) {
                return@objectApproach
            }
            message("You examine the rock for ores...")
            delay(4)
            val ore = EnumDefinitions.stringOrNull("mining_ores", target.def(this).stringId)
            if (ore == null) {
                message("This rock contains no ore.")
            } else {
                message("This rock contains ${ore.toLowerSpaceCase()}.")
            }
        }
    }

    fun hasRequirements(player: Player, pickaxe: Item?, message: Boolean = false): Boolean {
        if (pickaxe == null) {
            if (message) {
                player.message("You need a pickaxe to mine this rock.")
                player.message("You do not have a pickaxe which you have the mining level to use.")
            }
            return false
        }
        return player.hasRequirementsToUse(pickaxe, message, setOf(Skill.Mining, Skill.Firemaking))
    }

    fun addOre(player: Player, ore: String): Boolean {
        if (ore == "stardust") {
            ShootingStarHandler.addStarDustCollected()
            val totalStarDust = player.inventory.count(ore) + player.bank.count(ore)
            if (totalStarDust >= 200) {
                player.message("You have the maximum amount of stardust but was still rewarded experience.")
                return true
            }
        }
        val added = player.inventory.add(ore)
        if (added) {
            player.message("You manage to mine some ${ore.toLowerSpaceCase()}.")
        } else {
            player.inventoryFull()
        }
        return added
    }

    fun deplete(obj: GameObject, life: Int): Boolean {
        if (obj.id.startsWith("crashed_star_tier_")) {
            ShootingStarHandler.handleMinedStarDust(obj)
            return false
        }
        if (life >= 0) {
            GameObjects.replace(obj, "depleted${obj.id.dropWhile { it != '_' }}", ticks = life)
            return true
        }
        return false
    }
}
