package sim.engine.inv.restrict

import sim.engine.entity.item.Item

interface ItemRestrictionRule {
    fun restricted(id: String): Boolean
    fun replacement(id: String): Item? = null
}
