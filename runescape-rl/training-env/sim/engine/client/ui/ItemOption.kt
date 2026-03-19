package sim.engine.client.ui

import sim.engine.entity.item.Item

data class ItemOption(
    val item: Item,
    val slot: Int,
    val inventory: String,
    val option: String,
) {
    override fun toString(): String {
        return "$option:${item.id}:${inventory} slot=$slot"
    }
}