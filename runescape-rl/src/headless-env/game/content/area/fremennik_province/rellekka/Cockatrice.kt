package content.area.fremennik_province.rellekka

import sim.engine.Script
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.equip.equipped
import sim.network.login.protocol.visual.update.player.EquipSlot

class Cockatrice : Script {
    init {
        npcCondition("mirror_shield") { it is Player && it.equipped(EquipSlot.Shield).id == "mirror_shield" }
        npcCondition("no_mirror_shield") { it is Player && it.equipped(EquipSlot.Shield).id != "mirror_shield" }
    }
}
