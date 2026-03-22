package content.skill.melee.armour.barrows

import content.entity.player.effect.energy.runEnergy
import sim.engine.Script
import sim.engine.entity.character.Character
import sim.engine.entity.character.player.Player
import sim.engine.inv.ItemAdded
import sim.engine.inv.ItemRemoved
import sim.types.random

class ToragsSet : Script {

    init {
        playerSpawn {
            if (hasFullSet()) {
                set("torags_set_effect", true)
            }
        }

        for (slot in BarrowsArmour.slots) {
            itemAdded("torags_*", "worn_equipment", slot, ::added)
            itemRemoved("torags_*", "worn_equipment", slot, ::removed)
        }

        combatAttack("melee", handler = ::attack)
    }

    fun attack(source: Character, attack: sim.engine.entity.character.mode.combat.CombatAttack) {
        val (target, damage, _, weapon) = attack
        if (damage <= 0 || target !is Player || !weapon.id.startsWith("torags_hammers") || !source.contains("torags_set_effect") || random.nextInt(4) != 0) {
            return
        }
        if (target.runEnergy > 0) {
            target.runEnergy -= target.runEnergy / 5
            target.gfx("torags_effect")
        }
    }

    fun added(player: Player, update: ItemAdded) {
        if (player.hasFullSet()) {
            player["torags_set_effect"] = true
        }
    }

    fun removed(player: Player, update: ItemRemoved) {
        player.clear("torags_set_effect")
    }

    fun Player.hasFullSet() = BarrowsArmour.hasSet(
        this,
        "torags_hammers",
        "torags_helm",
        "torags_platebody",
        "torags_platelegs",
    )
}
