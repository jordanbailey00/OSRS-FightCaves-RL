package content.area.morytania.slayer_tower

import content.entity.player.equip.Equipment
import sim.engine.Script
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.equip.equipped
import sim.network.login.protocol.visual.update.player.EquipSlot

class AberrantSpectre : Script {

    init {
        npcCondition("nose_peg") { target ->
            target is Player && Equipment.isNosePeg(target.equipped(EquipSlot.Hat).id)
        }
        npcCondition("no_nose_peg") { target ->
            !(target is Player && Equipment.isNosePeg(target.equipped(EquipSlot.Hat).id))
        }
    }
}
