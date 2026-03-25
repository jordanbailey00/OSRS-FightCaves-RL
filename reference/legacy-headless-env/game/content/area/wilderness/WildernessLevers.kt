package content.area.wilderness

import content.entity.obj.ObjectTeleports
import content.entity.player.dialogue.type.choice
import content.entity.player.dialogue.type.statement
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.variable.start
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Teleport
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.sound
import sim.engine.entity.obj.GameObject
import sim.engine.queue.queue

class WildernessLevers(val teleports: ObjectTeleports) : Script {

    init {
        objTeleportTakeOff("Pull", "lever_*") { target, option ->
            if (target.def(this).stringId == "lever_ardougne_edgeville" && get("wilderness_lever_warning", true)) {
                queue("wilderness_lever_warning") {
                    statement("Warning! Pulling the lever will teleport you deep into the Wilderness.")
                    choice("Are you sure you wish to pull it?") {
                        option("Yes I'm brave.") {
                            pullLever(target)
                        }
                        option("Eeep! The Wilderness... No thank you.") {
                            return@option
                        }
                        option("Yes please, don't show this message again.") {
                            player["wilderness_lever_warning"] = false
                            pullLever(target)
                        }
                    }
                }
                return@objTeleportTakeOff Teleport.CANCEL
            }
            pullLever(this)
            queue("pull_lever") {
                val definition = teleports.get("Pull")[target.tile.id]!!
                val tile = teleports.teleportTile(player, definition)
                anim("teleport_modern")
                sound("teleport")
                gfx("teleport_modern")
                delay(3)
                tele(tile)
                Teleport.land(player, target, option)
            }
            return@objTeleportTakeOff Teleport.CANCEL
        }

        objTeleportLand("Pull", "lever_*") { target, _ ->
            sound("teleport_land")
            gfx("teleport_land_modern")
            anim("teleport_land_modern")
            val message: String = target.def(this).getOrNull("land_message") ?: return@objTeleportLand
            message(message, ChatType.Filter)
        }
    }

    fun pullLever(player: Player) {
        player.message("You pull the lever...", ChatType.Filter)
        player.anim("pull_lever")
        player.start("movement_delay", 3)
    }

    suspend fun Player.pullLever(target: GameObject) {
        pullLever(this)
        delay(2)
        anim("teleport_modern")
        sound("teleport")
        gfx("teleport_modern")
        val definition = teleports.get("Pull")[target.tile.id]!!
        teleports.teleportContinue(this, definition, target, delay = 3)
    }
}
