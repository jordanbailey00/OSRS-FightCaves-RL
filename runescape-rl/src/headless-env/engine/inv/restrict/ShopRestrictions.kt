package sim.engine.inv.restrict

import sim.engine.entity.item.Item

class ShopRestrictions(
    private val items: Array<Item>,
) : ItemRestrictionRule {
    override fun restricted(id: String): Boolean = items.indexOfFirst { it.id == id } == -1
}
