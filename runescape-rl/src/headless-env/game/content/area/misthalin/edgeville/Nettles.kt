package content.area.misthalin.edgeville

import content.entity.combat.hit.damage
import content.entity.effect.toxin.poisonDamage
import content.entity.player.dialogue.type.statement
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.equip.equipped
import sim.engine.entity.character.player.male
import sim.engine.entity.character.sound
import sim.engine.entity.obj.remove
import sim.engine.inv.add
import sim.engine.inv.inventory
import sim.network.login.protocol.visual.update.player.EquipSlot

class Nettles : Script {

    init {
        objectOperate("Pick", "nettles") { (target) ->
            if (inventory.isFull()) {
                // Rs3 and osrs has this statement.
                statement("You can't carry any more nettles.")
                return@objectOperate
            }
            if (!equipped(EquipSlot.Hands).id.contains("gloves")) {
                walkToDelay(target.tile)
                anim("climb_down")
                delay(2)
                damage(20, 0, "poison", this)
                poisonDamage = 0
                if (male) {
                    sound("man_defend")
                } else {
                    sound("woman_defend")
                }
                // Rs3 and osrs has this message.
                message("You have been stung by the nettles.")
                return@objectOperate
            }
            walkTo(target.tile)
            delay(1)
            anim("climb_down")
            sound("pick")
            // Rs3 and osrs has this message.
            message("You pick a handful of nettles.")
            inventory.add("nettles")
            target.remove(15)
        }
    }
}
