package content.skill.magic.book.lunar

import content.entity.effect.toxin.curePoison
import content.entity.effect.toxin.poisoned
import content.skill.magic.spell.removeSpellItems
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.variable.start
import sim.engine.data.definition.SpellDefinitions
import sim.engine.entity.character.player.name
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.sound

class CureOther(val definitions: SpellDefinitions) : Script {

    init {
        onPlayerApproach("lunar_spellbook:cure_other") { (target) ->
            approachRange(2)
            if (!target.poisoned) {
                message("This player is not poisoned.")
                return@onPlayerApproach
            }
            if (!get("accept_aid", true)) {
                message("This player is not currently accepting aid.") // TODO proper message
                return@onPlayerApproach
            }
            if (!removeSpellItems("cure_other")) {
                return@onPlayerApproach
            }
            val definition = definitions.get("cure_other")
            start("movement_delay", 2)
            anim("lunar_cast")
            target.gfx("cure_other")
            sound("cure_other")
            experience.add(Skill.Magic, definition.experience)
            target.curePoison()
            target.sound("cure_other_impact")
            target.message("You have been cured by $name.")
        }
    }
}
