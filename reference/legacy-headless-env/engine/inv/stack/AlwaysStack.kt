package sim.engine.inv.stack

/**
 * Forces all items, even un-stackable items, to be stacked
 */
object AlwaysStack : ItemStackingRule {
    override fun stackable(id: String) = true
}
