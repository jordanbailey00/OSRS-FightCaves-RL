package sim.engine.entity.item

import sim.cache.definition.data.ItemDefinition
import sim.engine.entity.character.player.equip.EquipType
import sim.network.login.protocol.visual.update.player.EquipSlot

val ItemDefinition.slot: EquipSlot
    get() = this["slot", EquipSlot.None]

val Item.slot: EquipSlot
    get() = def.slot

val ItemDefinition.type: EquipType
    get() = this["type", EquipType.None]

val Item.type: EquipType
    get() = def.type
