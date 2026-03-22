package content.skill.magic.book.lunar

import content.entity.effect.toxin.curePoison
import content.entity.effect.toxin.poisoned
import content.skill.magic.spell.removeSpellItems
import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.SpellDefinitions
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.name
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.sound

class CureGroup(val definitions: SpellDefinitions) : Script {

    init {
        interfaceOption("Cast", "lunar_spellbook:cure_group") {
            val spell = it.component
            if (!removeSpellItems(spell)) {
                return@interfaceOption
            }
            val definition = definitions.get(spell)
            anim("lunar_cast_group")
            sound(spell)
            experience.add(Skill.Magic, definition.experience)
            Players
                .filter { other -> other.tile.within(tile, 1) && other.poisoned && get("accept_aid", true) }
                .forEach { target ->
                    target.gfx(spell)
                    target.sound("cure_other_impact")
                    target.curePoison()
                    target.message("You have been cured by $name")
                }
        }
    }
}
