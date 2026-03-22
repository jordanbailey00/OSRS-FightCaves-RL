package content.skill.magic.book.lunar

import content.entity.player.command.find
import content.skill.magic.spell.removeSpellItems
import sim.engine.Script
import sim.engine.client.command.adminCommand
import sim.engine.client.command.commandSuggestion
import sim.engine.client.message
import sim.engine.client.variable.remaining
import sim.engine.client.variable.start
import sim.engine.data.definition.AccountDefinitions
import sim.engine.data.definition.SpellDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.sound
import sim.engine.timer.epochSeconds

class VengeanceOther(
    val accounts: AccountDefinitions,
    val definitions: SpellDefinitions,
) : Script {

    init {
        onPlayerApproach("lunar_spellbook:vengeance_other") { (target) ->
            approachRange(2)
            if (target.contains("vengeance")) {
                message("This player already has vengeance cast.")
                return@onPlayerApproach
            }
            if (remaining("vengeance_delay", epochSeconds()) > 0) {
                message("You can only cast vengeance spells once every 30 seconds.")
                return@onPlayerApproach
            }
            if (!get("accept_aid", true)) {
                message("This player is not currently accepting aid.") // TODO proper message
                return@onPlayerApproach
            }
            if (!removeSpellItems("vengeance_other")) {
                return@onPlayerApproach
            }
            vengeance(target)
        }
        adminCommand("veng", desc = "Give player vengence", handler = ::veng)
        commandSuggestion("veng", "vengeance", "vengeance_other")
    }

    private fun Player.vengeance(target: Player) {
        val definition = definitions.get("vengeance_other")
        start("movement_delay", 2)
        anim("lunar_cast")
        target.gfx("vengeance_other")
        sound("vengeance_other")
        experience.add(Skill.Magic, definition.experience)
        target["vengeance"] = true
        start("vengeance_delay", definition["delay_seconds"], epochSeconds())
    }

    fun veng(player: Player, args: List<String>) {
        val target = Players.find(player, args.getOrNull(0)) ?: return
        if (target.contains("vengeance")) {
            player.message("That player already has vengeance cast.")
            return
        }
        player.vengeance(target)
    }
}
