package content.entity.player.inv.item.destroy

import com.github.michaelbull.logging.InlineLogger
import content.entity.player.dialogue.type.destroy
import sim.engine.Script
import sim.engine.client.ui.ItemOption
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.sound
import sim.engine.inv.Items
import sim.engine.inv.inventory
import sim.engine.inv.remove

class ItemDestroy : Script {

    val logger = InlineLogger()

    init {
        itemOption("Destroy", handler = ::destroy)
        itemOption("Dismiss", handler = ::destroy)
        itemOption("Release", handler = ::destroy)
    }

    suspend fun destroy(player: Player, it: ItemOption) {
        val (item, slot, _, option) = it
        if (item.isEmpty() || item.amount <= 0) {
            logger.info { "Error destroying item $item for $player" }
            return
        }
        val message = item.def[
            "destroy", """
                Are you sure you want to ${option.lowercase()} ${item.def.name}?
                You won't be able to reclaim it.
            """,
        ]
        val destroy = player.destroy(item.id, message)
        if (!destroy) {
            return
        }
        if (!Items.destructible(player, item)) {
            return
        }
        if (player.inventory.remove(slot, item.id, item.amount)) {
            player.sound("destroy_object")
            Items.destroyed(player, item)
            logger.info { "$player destroyed item $item" }
        } else {
            logger.info { "Error destroying item $item for $player" }
        }
    }
}
