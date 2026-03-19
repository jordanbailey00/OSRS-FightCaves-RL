package content.area.morytania.slayer_tower

import content.entity.combat.hit.hit
import content.entity.obj.door.enterDoor
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Teleport
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.exp.exp
import sim.engine.entity.character.player.skill.level.Level
import sim.engine.entity.character.player.skill.level.Level.has
import sim.engine.entity.item.Item
import sim.engine.entity.obj.GameObject
import sim.engine.entity.obj.GameObjects

class SlayerTower : Script {

    init {
        objectSpawn("slayer_tower_entrance_door_*_opened") {
            val statue = if (id == "slayer_tower_entrance_door_west_opened") {
                GameObjects.findOrNull(tile.add(-2, -2), "slayer_tower_statue")
            } else {
                GameObjects.findOrNull(tile.add(1, -2), "slayer_tower_statue")
            } ?: return@objectSpawn
            statue.anim("slayer_tower_statue_stand")
        }

        objectDespawn("slayer_tower_entrance_door_*_opened") {
            val statue = if (id == "slayer_tower_entrance_door_west_opened") {
                GameObjects.findOrNull(tile.add(-2, -2), "slayer_tower_statue")
            } else {
                GameObjects.findOrNull(tile.add(1, -2), "slayer_tower_statue")
            } ?: return@objectDespawn
            statue.anim("slayer_tower_statue_hide")
        }

        objTeleportTakeOff("Climb-up", "slayer_tower_chain*", ::takeOff)
        objTeleportTakeOff("Climb-down", "slayer_tower_chain*", ::takeOff)

        objTeleportLand("Climb-up", "slayer_tower_chain*", ::land)
        objTeleportLand("Climb-down", "slayer_tower_chain*", ::land)

        objectOperate("Open", "slayer_tower_door*_closed") { (target) ->
            enterDoor(target)
        }
    }

    fun takeOff(player: Player, target: GameObject, option: String): Int {
        val requirement = if (target.tile.x == 3422 && target.tile.y == 3550) 61 else 71
        if (!player.has(Skill.Agility, requirement)) {
            player.message("You need an Agility level of $requirement to negotiate this obstacle.")
            return Teleport.CANCEL
        }
        val success = Level.success(player.levels.get(Skill.Agility), 90) // Unknown success rate
        player["slayer_chain_success"] = success
        if (!success) {
            player.hit(player, damage = 20, offensiveType = "damage", weapon = Item.EMPTY)
            player.message("You rip your hands to pieces on the chain as you climb.", type = ChatType.Filter)
        }
        return Teleport.CONTINUE
    }

    fun land(player: Player, target: GameObject, option: String) {
        player.exp(Skill.Agility, if (player["slayer_chain_success", true]) 3.0 else 6.0)
    }
}
