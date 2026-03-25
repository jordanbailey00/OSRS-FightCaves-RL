package content.skill.magic.jewellery

import sim.engine.Script
import sim.engine.entity.character.player.equip.equipped
import sim.network.login.protocol.visual.update.player.EquipSlot

class RingOfWealth : Script {

    init {
        playerSpawn {
            set("wearing_ring_of_wealth", equipped(EquipSlot.Ring).id == "ring_of_wealth")
        }

        itemAdded("ring_of_wealth", "worn_equipment", EquipSlot.Ring) {
            set("wearing_ring_of_wealth", true)
        }

        itemRemoved("ring_of_wealth", "worn_equipment", EquipSlot.Ring) {
            set("wearing_ring_of_wealth", false)
        }
    }
}
