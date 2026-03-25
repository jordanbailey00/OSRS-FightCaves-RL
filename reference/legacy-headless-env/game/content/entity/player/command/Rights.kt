package content.entity.player.command

import net.pearx.kasechange.toSentenceCase
import sim.engine.Script
import sim.engine.client.command.adminCommand
import sim.engine.client.command.commandSuggestion
import sim.engine.client.command.stringArg
import sim.engine.client.message
import sim.engine.data.Settings
import sim.engine.data.definition.AccountDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.PlayerRights
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.name
import sim.engine.entity.character.player.rights

class Rights(val accounts: AccountDefinitions) : Script {

    init {
        playerSpawn {
            if (name == Settings.getOrNull("development.admin.name") && rights != PlayerRights.Admin) {
                rights = PlayerRights.Admin
                message("Rights set to Admin. Please re-log to activate.")
            }
        }

        val rights = PlayerRights.entries.map { it.name }.toSet()
        adminCommand("rights", stringArg("player-name", autofill = accounts.displayNames.keys), stringArg("rights-name", autofill = rights), desc = "Set the rights for another player", handler = ::grant)
        commandSuggestion("rights", "promote")
    }

    fun grant(player: Player, args: List<String>) {
        val right = args[1]
        val rights: PlayerRights
        try {
            rights = PlayerRights.valueOf(right.toSentenceCase())
        } catch (e: IllegalArgumentException) {
            player.message("No rights found with the name: '${right.toSentenceCase()}'.")
            return
        }
        val target = Players.firstOrNull { it.name.equals(args[0], true) }
        if (target == null) {
            player.message("Unable to find player '${args[0]}' online.", ChatType.Console)
            return
        }
        target.rights = rights
        player.message("${player.name} rights set to $rights.")
        target.message("${player.name} granted you $rights rights. Please re-log to activate.")
    }
}
