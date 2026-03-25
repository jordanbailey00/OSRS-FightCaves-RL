package content.skill.melee.armour

import sim.engine.Script
import sim.engine.data.definition.Areas
import sim.engine.entity.character.player.equip.equipped
import sim.network.login.protocol.visual.update.player.EquipSlot

class CastleWarsBrace : Script {

    init {
        entered("castle_wars") {
            if (equipped(EquipSlot.Hands).id.startsWith("castle_wars_brace")) {
                set("castle_wars_brace", true)
            }
        }

        exited("castle_wars") {
            if (equipped(EquipSlot.Hands).id.startsWith("castle_wars_brace")) {
                clear("castle_wars_brace")
            }
        }

        // TODO should be activated on game start not equip.
        itemAdded("castle_wars_brace*", "worn_equipment", EquipSlot.Hands) {
            if (tile in Areas["castle_wars"]) {
                set("castle_wars_brace", true)
            }
        }

        itemRemoved("castle_wars_brace*", "worn_equipment", EquipSlot.Hands) {
            if (tile in Areas["castle_wars"]) {
                clear("castle_wars_brace")
            }
        }
    }
}
