package content.entity.player.equip

import sim.engine.Script
import sim.engine.entity.character.player.flagAppearance
import sim.network.login.protocol.visual.update.player.Body
import sim.network.login.protocol.visual.update.player.BodyPart
import sim.network.login.protocol.visual.update.player.EquipSlot

class Appearance : Script {

    init {
        slotChanged {
            if (needsUpdate(it.index, body)) {
                flagAppearance()
            }
        }
    }

    fun needsUpdate(index: Int, parts: Body): Boolean {
        val slot = EquipSlot.by(index)
        val part = BodyPart.by(slot) ?: return false
        return parts.updateConnected(part)
    }
}
