package content.skill.magic.spell

import content.skill.melee.weapon.attackRange
import sim.engine.Script
import sim.engine.data.definition.InterfaceDefinitions
import sim.engine.entity.character.player.Player
import sim.network.login.protocol.visual.update.player.EquipSlot

class Autocast : Script {

    init {
        variableSet("autocast") { _, _, to ->
            if (to == null) {
                clear("autocast_spell")
            }
        }

        interfaceOption("Autocast", id = "*_spellbook:*") {
            toggle(it.id, it.component)
        }

        slotChanged("worn_equipment", EquipSlot.Weapon) {
            clear("autocast")
        }
    }

    fun Player.toggle(id: String, component: String) {
        val value: Int? = InterfaceDefinitions.getComponent(id, component)?.getOrNull("cast_id")
        if (value == null || get("autocast", 0) == value) {
            clear("autocast")
        } else {
            set("autocast_spell", component)
            attackRange = 8
            set("autocast", value)
        }
    }
}
