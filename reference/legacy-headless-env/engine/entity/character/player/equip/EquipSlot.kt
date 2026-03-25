package sim.engine.entity.character.player.equip

import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item
import sim.engine.inv.equipment
import sim.network.login.protocol.visual.update.player.EquipSlot

fun Player.has(slot: EquipSlot): Boolean = equipment[slot.index].isNotEmpty()

fun Player.equipped(slot: EquipSlot): Item = equipment[slot.index]
