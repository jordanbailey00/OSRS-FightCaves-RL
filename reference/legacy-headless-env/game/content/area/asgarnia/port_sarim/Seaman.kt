package content.area.asgarnia.port_sarim

import content.entity.obj.ship.boatTravel
import content.entity.player.dialogue.Happy
import content.entity.player.dialogue.Neutral
import content.entity.player.dialogue.Quiz
import content.entity.player.dialogue.Sad
import content.entity.player.dialogue.type.choice
import content.entity.player.dialogue.type.npc
import content.entity.player.dialogue.type.player
import content.entity.player.dialogue.type.statement
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.Player
import sim.engine.inv.inventory
import sim.engine.inv.remove
import sim.types.Tile

class Seaman : Script {

    init {
        npcOperate("Talk-to", "seaman_lorris*,captain_tobias*,seaman_thresnor*") {
            npc<Quiz>("Do you want to go on a trip to Karamja?")
            npc<Neutral>("The trip will cost you 30 coins.")
            choice {
                option<Happy>("Yes please.") {
                    if (!inventory.remove("coins", 30)) {
                        player<Sad>("Oh dear, I don't seem to have enough money.")
                        return@option
                    }
                    travel()
                }
                option<Neutral>("No, thank you.")
            }
        }

        npcOperate("Pay-fare", "seaman_lorris*,captain_tobias*,seaman_thresnor*") {
            if (!inventory.remove("coins", 30)) {
                message("You do not have enough money for that.")
                return@npcOperate
            }
            travel()
        }
    }

    private suspend fun Player.travel() {
        message("You pay 30 coins and board the ship.")
        boatTravel("port_sarim_to_karamja", 7, Tile(2956, 3143, 1))
        statement("The ship arrives at Karamja.")
    }
}
