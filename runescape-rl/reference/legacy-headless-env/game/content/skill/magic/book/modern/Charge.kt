package content.skill.magic.book.modern

import content.skill.magic.spell.removeSpellItems
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.chat.plural
import sim.engine.client.variable.hasClock
import sim.engine.client.variable.remaining
import sim.engine.client.variable.start
import sim.engine.data.definition.SpellDefinitions
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.sound
import sim.engine.timer.TICKS

class Charge(val definitions: SpellDefinitions) : Script {

    init {
        interfaceOption("Cast", "modern_spellbook:charge") {
            if (hasClock("charge_delay")) {
                val remaining = TICKS.toSeconds(remaining("charge_delay"))
                message("You must wait another $remaining ${"second".plural(remaining)} before casting this spell again.")
                return@interfaceOption
            }
            val spell = it.component
            if (!removeSpellItems(spell)) {
                return@interfaceOption
            }

            val definition = definitions.get(spell)
            anim(spell)
            sound(spell)
            experience.add(Skill.Magic, definition.experience)
            start("charge", definition["effect_ticks"])
            start("charge_delay", definition["delay_ticks"])
        }
    }
}
