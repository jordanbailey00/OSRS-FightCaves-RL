package content.skill.farming

import content.entity.player.inv.item.addOrDrop
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Interpolation
import sim.engine.entity.character.player.skill.level.Level
import sim.engine.entity.character.sound
import sim.engine.entity.obj.GameObject
import sim.engine.inv.inventory
import sim.engine.queue.weakQueue

class FarmingPatchClear : Script {

    init {
        objectOperate("Clear", "*_tree_farming_stump") { (target) ->
            message("You start digging up the tree stump.", type = ChatType.Filter)
            clear(this, target, stump = true)
        }

        itemOnObjectOperate("spade", "*_tree_farming_stump") { (target) ->
            message("You start digging up the tree stump.", type = ChatType.Filter)
            clear(this, target, stump = true)
        }

        objectOperate("Clear", "*_dead") { (target) ->
            message("You start digging the farming patch...", type = ChatType.Filter)
            clear(this, target)
        }

        itemOnObjectOperate("spade", "*_dead") { (target) ->
            message("You start digging the farming patch...", type = ChatType.Filter)
            clear(this, target)
        }
    }

    private fun clear(player: Player, obj: GameObject, count: Int = -1, stump: Boolean = false) {
        if (count == 0) {
            return
        }
        if (!player.inventory.contains("spade")) {
            player.message("You need a spade to clear a farming patch.")
            return
        }
        player.anim("human_dig")
        player.sound("dig_spade")
        player.weakQueue("clear_patch", 2) {
            val variable = obj.id
            if (Level.success(player.levels.get(Skill.Farming), 60)) { // TODO proper chances
                if (stump) {
                    val type = obj.def(player).stringId.removeSuffix("_tree_farming_stump")
                    player.addOrDrop("${type}_roots")
                    when (count) {
                        -1 -> {
                            val level = when (type) {
                                "oak" -> 15
                                "willow" -> 30
                                "maple" -> 45
                                "yew" -> 60
                                "magic" -> 75
                                else -> return@weakQueue
                            }
                            val roots = Interpolation.lerp(player.levels.get(Skill.Farming), level..99, 1..4)
                            clear(player, obj, roots - 1, stump = true)
                        }
                        1 -> {
                            player.message("You dig up the tree stump.", type = ChatType.Filter)
                            player[variable] = "weeds_0"
                        }
                        else -> clear(player, obj, count - 1, stump = true)
                    }
                } else {
                    player.message("You have successfully cleared this patch for new crops.", type = ChatType.Filter)
                    player[variable] = "weeds_0"
                }
            } else {
                clear(player, obj, count - 1)
            }
        }
    }
}
