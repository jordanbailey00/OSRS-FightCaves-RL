package sim.engine.inv.restrict

import sim.engine.data.definition.ItemDefinitions
import sim.engine.entity.item.Item

class ValidItemRestriction : ItemRestrictionRule {
    override fun restricted(id: String): Boolean = id.isBlank() || !ItemDefinitions.contains(id)

    override fun replacement(id: String): Item? {
        val definition = ItemDefinitions.getOrNull(id) ?: return null
        val replacement: String = definition.getOrNull("degrade") ?: return null
        if (replacement == "destroy") {
            return Item.EMPTY
        }
        val replaceDefinition = ItemDefinitions.get(replacement)
        return Item(replacement, replaceDefinition["charges", 1]) // Use charges or default to 1 for amount
    }
}
