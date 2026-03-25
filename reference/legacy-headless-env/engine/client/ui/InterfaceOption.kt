package sim.engine.client.ui

import sim.engine.entity.item.Item

data class InterfaceOption(
    val item: Item,
    val itemSlot: Int,
    val option: String,
    val optionIndex: Int,
    val interfaceComponent: String,
) {
    val id: String
        get() = interfaceComponent.substringBefore(":")
    val component: String
        get() = interfaceComponent.substringAfter(":")

    override fun toString(): String {
        return "$option:$interfaceComponent item=${item}, slot=${itemSlot}, index=${optionIndex}"
    }
}
