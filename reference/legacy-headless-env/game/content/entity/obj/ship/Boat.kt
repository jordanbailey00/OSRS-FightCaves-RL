package content.entity.obj.ship

import content.quest.startCutscene
import sim.engine.client.sendScript
import sim.engine.client.ui.open
import sim.engine.entity.character.jingle
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.player.Player
import sim.types.Tile

suspend fun Player.boatTravel(journey: String, delay: Int, destination: Tile) {
    val cutscene = startCutscene("ship_travel")
    cutscene.onEnd {
        tele(destination)
    }
    tele(cutscene.instance.tile, clearInterfaces = false)
    sendScript("clear_ships")
    jingle("sailing_journey")
    open("journey_ship")
    set("ships_set_destination", journey)
    delay(delay)
    cutscene.end()
}
