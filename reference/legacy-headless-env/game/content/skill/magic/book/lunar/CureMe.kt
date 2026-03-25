package content.skill.magic.book.lunar

import content.entity.effect.toxin.curePoison
import content.entity.effect.toxin.poisoned
import content.skill.magic.spell.removeSpellItems
import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.SpellDefinitions
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.sound

class CureMe(val definitions: SpellDefinitions) : Script {

    init {
        interfaceOption("Cast", "lunar_spellbook:cure_me") {
            val spell = it.component
            if (!poisoned) {
                message("You are not poisoned.")
                return@interfaceOption
            }
            if (!removeSpellItems(spell)) {
                return@interfaceOption
            }
            val definition = definitions.get(spell)
            anim("lunar_cast")
            gfx(spell)
            sound(spell)
            experience.add(Skill.Magic, definition.experience)
            curePoison()
        }
    }
}
