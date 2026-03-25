package content.skill.magic.book.lunar

import content.entity.combat.hit.damage
import content.skill.magic.spell.removeSpellItems
import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.SpellDefinitions
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.name
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.sound

class HealGroup(val definitions: SpellDefinitions) : Script {

    init {
        interfaceOption("Cast", "lunar_spellbook:heal_group") {
            val spell = it.component
            if (levels.get(Skill.Constitution) < levels.getMax(Skill.Constitution) * 0.11) {
                message("You don't have enough life points.")
                return@interfaceOption
            }
            if (!removeSpellItems(spell)) {
                return@interfaceOption
            }
            val definition = definitions.get(spell)
            var healed = 0
            val amount = (levels.get(Skill.Constitution) * 0.75).toInt() + 5
            anim("lunar_cast")
            sound(spell)
            val group = Players
                .filter { other -> other != this && other.tile.within(tile, 1) && other.levels.getOffset(Skill.Constitution) < 0 && get("accept_aid", true) }
                .take(5)
            group.forEach { target ->
                target.gfx(spell)
                target.sound("heal_other_impact")
                experience.add(Skill.Magic, definition.experience)
                healed += target.levels.restore(Skill.Constitution, amount / group.size)
                target.message("You have been healed by $name.")
            }
            if (healed > 0) {
                damage(healed, delay = 2)
            }
        }
    }
}
