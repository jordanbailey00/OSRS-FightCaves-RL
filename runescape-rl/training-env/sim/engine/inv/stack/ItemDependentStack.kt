package sim.engine.inv.stack

import sim.engine.data.definition.ItemDefinitions

/**
 * Checks individual items are stackable
 */
object ItemDependentStack : ItemStackingRule {
    override fun stackable(id: String) = ItemDefinitions.get(id).stackable == 1
}
