package content.skill.melee.armour

import content.entity.combat.hit.directHit
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.equip.equipped
import sim.engine.inv.charges
import sim.engine.inv.discharge
import sim.engine.inv.equipment
import sim.network.login.protocol.visual.update.player.EquipSlot

class RingOfRecoil : Script {

    init {
        combatDamage { (source, type, damage) ->
            if (source == this || type == "deflect" || type == "poison" || type == "disease" || type == "healed" || damage < 1) {
                return@combatDamage
            }
            if (equipped(EquipSlot.Ring).id != "ring_of_recoil") {
                return@combatDamage
            }
            if (source is NPC && source.def["immune_deflect", false]) {
                return@combatDamage
            }
            val charges = equipment.charges(this, EquipSlot.Ring.index)
            val deflect = (10 + (damage / 10)).coerceAtMost(charges)
            if (equipment.discharge(this, EquipSlot.Ring.index, deflect)) {
                source.directHit(deflect, "deflect", source = this)
            }
        }

        itemOption("Check", "ring_of_recoil", "worn_equipment") {
            val charges = equipment.charges(this, EquipSlot.Ring.index)
            message("You can inflict $charges more points of damage before a ring will shatter.")
        }
    }
}
