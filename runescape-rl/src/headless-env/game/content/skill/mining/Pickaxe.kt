package content.skill.mining

import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level.hasRequirementsToUse
import sim.engine.entity.item.Item
import sim.engine.inv.carriesItem

object Pickaxe {
    private val pickaxes = listOf(
        Item("dragon_pickaxe"),
        Item("volatile_clay_pickaxe"),
        Item("sacred_clay_pickaxe"),
        Item("inferno_adze"),
        Item("rune_pickaxe"),
        Item("adamant_pickaxe"),
        Item("mithril_pickaxe"),
        Item("steel_pickaxe"),
        Item("iron_pickaxe"),
        Item("bronze_pickaxe"),
    )

    fun best(player: Player): Item? = pickaxes.firstOrNull { pickaxe -> player.hasRequirementsToUse(pickaxe, skills = setOf(Skill.Mining, Skill.Firemaking)) && player.carriesItem(pickaxe.id) }
}
