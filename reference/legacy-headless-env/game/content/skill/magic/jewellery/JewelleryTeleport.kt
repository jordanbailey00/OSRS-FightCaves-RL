package content.skill.magic.jewellery

import sim.engine.client.ui.closeInterfaces
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.sound
import sim.engine.inv.discharge
import sim.engine.map.collision.random
import sim.engine.queue.ActionPriority
import sim.engine.queue.queue
import sim.types.Area
import sim.types.Tile

fun jewelleryTeleport(player: Player, inventory: String, slot: Int, area: Area): Boolean = itemTeleport(player, inventory, slot, area, "jewellery")

fun Player.teleport(tile: Tile, type: String, force: Boolean = false) = itemTeleport(this, tile, type, force)

fun itemTeleport(player: Player, inventory: String, slot: Int, area: Area, type: String): Boolean {
    if (player.queue.contains(ActionPriority.Normal) || !player.inventories.inventory(inventory).discharge(player, slot)) {
        return false
    }
    return itemTeleport(player, area, type)
}

fun itemTeleport(player: Player, area: Area, type: String, force: Boolean = false): Boolean {
    if (!force && player.queue.contains(ActionPriority.Normal)) {
        return false
    }
    return itemTeleport(player, area.random(player) ?: return false, type, force)
}

fun itemTeleport(player: Player, tile: Tile, type: String, force: Boolean = false): Boolean {
    if (!force && player.queue.contains(ActionPriority.Normal)) {
        return false
    }
    player.closeInterfaces()
    player.queue("teleport_$type", onCancel = null) {
        player.sound("teleport")
        player.gfx("teleport_$type")
        player.animDelay("teleport_$type")
        player.tele(tile)
        val int = player.anim("teleport_land_$type")
        if (int == -1) {
            player.clearAnim()
        } else {
            player.gfx("teleport_land_$type")
        }
    }
    return true
}
