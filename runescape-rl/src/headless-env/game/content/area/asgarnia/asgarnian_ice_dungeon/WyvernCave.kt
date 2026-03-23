package content.area.asgarnia.asgarnian_ice_dungeon

import content.entity.player.dialogue.type.choice
import content.entity.player.dialogue.type.statement
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.player.skill.Skill
import sim.types.Tile

class WyvernCave : Script {

    init {
        objectOperate("Exit", "ice_dungeon_wyvern_cave_exit") {
            val slayerLevel = levels.get(Skill.Slayer)
            if (slayerLevel < 72) {
                statement("You need a Slayer level of 72 to enter this cave.")
                return@objectOperate
            }
            statement("It's very cold in there... Are you sure you want to enter?")
            choice {
                option("Yes, I'll brave the cold.") {
                    message("You squeeze through the icy gap...")
                    tele(Tile(3056, 9555, 0)) // Location inside Ice Dungeon for Wyverns
                }
                option("No, it's too cold for me.") {
                    message("You decide to stay where it's warmer.")
                }
            }
        }

        objectOperate("Enter", "ice_dungeon_wyvern_cave_enter") {
            statement("This passage seems to lead back into the main Ice Dungeon.")
            choice {
                option("Leave the cave.") {
                    message("You crawl back through the gap.")
                    tele(Tile(3056, 9562, 0)) // Just outside the entrance
                }
                option("Stay in the cave.") {
                    message("You decide to stay here a while longer.")
                }
            }
        }
    }
}
