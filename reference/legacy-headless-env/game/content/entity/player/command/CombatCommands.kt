package content.entity.player.command

import sim.engine.Script
import sim.engine.client.command.adminCommand
import sim.engine.client.command.intArg
import sim.engine.client.command.stringArg
import sim.engine.data.definition.AccountDefinitions
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.skill.Skill
import sim.engine.inv.add
import sim.engine.inv.addToLimit
import sim.engine.inv.inventory
import sim.types.Tile

class CombatCommands(
    val accounts: AccountDefinitions,
) : Script {

    init {
        adminCommand(
            "boost",
            intArg("amount", "amount to boost by (default 25)", optional = true),
            desc = "Boosts all stats",
            handler = ::boost,
        )
        adminCommand("food", desc = "Fills inventory with food", handler = ::food)
        adminCommand("pots", desc = "Fills inventory with combat potions", handler = ::pots)
        adminCommand(
            "respawn",
            stringArg("player-name", autofill = accounts.displayNames.keys, optional = true),
            desc = "Teleport back to last death location",
            handler = ::respawn,
        )
    }

    fun respawn(player: Player, args: List<String>) {
        val target = Players.find(player, args.getOrNull(0)) ?: return
        val tile: Tile = target["death_tile"] ?: return
        target.tele(tile)
    }

    fun food(player: Player, args: List<String>) {
        player.inventory.addToLimit("rocktail", 28)
    }

    fun pots(player: Player, args: List<String>) {
        player.inventory.add("overload_4", "super_restore_4", "super_restore_4")
    }

    fun boost(player: Player, args: List<String>) {
        val amount = args.getOrNull(0)?.toIntOrNull() ?: 25
        for (skill in Skill.all) {
            player.levels.boost(skill, amount)
        }
    }
}
