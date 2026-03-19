package content.entity.player.command

import sim.engine.Script
import sim.engine.client.command.adminCommand
import sim.engine.client.command.modCommand
import sim.engine.client.command.stringArg
import sim.engine.client.message
import sim.engine.client.ui.chat.Colours
import sim.engine.client.ui.chat.toTag
import sim.engine.data.definition.AccountDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.engine.inv.clear
import sim.engine.inv.inventory
import sim.engine.inv.sendInventory

class InventoryCommands(val accounts: AccountDefinitions) : Script {

    init {
        modCommand("clear", desc = "Delete all items in the players inventory") {
            inventory.clear()
        }

        adminCommand("inv", stringArg("player-name", optional = true, autofill = accounts.displayNames.keys), desc = "Open the players inventory", handler = ::inv)
    }

    fun inv(player: Player, args: List<String>) {
        val target = Players.find(player, args.getOrNull(0)) ?: return
        if (target != player) {
            player.message("${Colours.RED_ORANGE.toTag()}Note: modifications won't effect target players inventory!")
        }
        player.sendInventory(target.inventory)
    }
}
