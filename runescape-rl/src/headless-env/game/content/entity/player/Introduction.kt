package content.entity.player

import content.bot.isBot
import content.entity.player.bank.bank
import content.entity.player.dialogue.type.statement
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.open
import sim.engine.client.variable.stop
import sim.engine.data.Settings
import sim.engine.entity.World
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.flagAppearance
import sim.engine.entity.character.player.name
import sim.engine.inv.add
import sim.engine.inv.inventory
import sim.engine.queue.queue

class Introduction : Script {

    fun welcome(player: Player) {
        player.message("Welcome to ${Settings["server.name"]}.", ChatType.Welcome)
        if (player.contains("creation")) {
            return
        }
        if (Settings["world.start.creation", true] && !player.isBot) {
            player["delay"] = -1
            World.queue("welcome_${player.name}", 1) {
                player.open("character_creation")
            }
        } else {
            player.flagAppearance()
            setup(player)
        }
    }

    init {
        playerSpawn(::welcome)

        interfaceClosed("character_creation") {
            flagAppearance()
            setup(this)
        }
    }

    fun setup(player: Player) {
        player.queue("welcome") {
            player.statement("Welcome to Lumbridge! To get more help, simply click on the Lumbridge Guide or one of the Tutors - these can be found by looking for the question mark icon on your minimap. If you find you are lost at any time, look for a signpost or use the Lumbridge Home Teleport spell.")
        }
        player.stop("delay")
        player["creation"] = System.currentTimeMillis()

        if (!Settings["world.setup.gear", true]) {
            return
        }
        player.bank.add("coins", 25)
        player.inventory.apply {
            add("bronze_hatchet")
            add("tinderbox")
            add("small_fishing_net")
            add("shrimp")
            add("bucket")
            add("empty_pot")
            add("bread")
            add("bronze_pickaxe")
            add("bronze_dagger")
            add("bronze_sword")
            add("wooden_shield")
            add("shortbow")
            add("bronze_arrow", 25)
            add("air_rune", 25)
            add("mind_rune", 15)
            add("water_rune", 6)
            add("earth_rune", 4)
            add("body_rune", 2)
        }
    }
}
