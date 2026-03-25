package content.entity.obj

import sim.engine.Script
import sim.engine.client.message
import sim.engine.data.definition.EnumDefinitions
import sim.engine.entity.character.player.chat.inventoryFull
import sim.engine.entity.character.sound
import sim.engine.entity.obj.remove
import sim.engine.inv.add
import sim.engine.inv.inventory
import sim.engine.timer.toTicks
import sim.types.random
import java.util.concurrent.TimeUnit

class Picking : Script {

    init {
        objectOperate("Pick") { (target) ->
            val item = EnumDefinitions.stringOrNull("pickable_item", target.id) ?: return@objectOperate
            if (inventory.add(item)) {
                sound("pick")
                anim("climb_down")
                val chance = EnumDefinitions.int("pickable_chance", target.id)
                if (random.nextInt(chance) == 0) {
                    val respawnDelay = EnumDefinitions.int("pickable_respawn_delay", target.id)
                    target.remove(TimeUnit.SECONDS.toTicks(respawnDelay))
                }
                val message = EnumDefinitions.string("pickable_message", target.id)
                message(message)
            } else {
                inventoryFull()
            }
        }
    }
}
