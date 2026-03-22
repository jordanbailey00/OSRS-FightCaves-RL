package content.area.asgarnia.asgarnian_ice_dungeon

import content.entity.combat.hit.Hit
import sim.engine.Script
import sim.engine.entity.character.Character
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.equip.equipped
import sim.engine.entity.item.Item
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.types.random

class SkeletalWyvern : Script {

    init {
        npcImpact("skeletal_wyvern", "ice_breath") { target ->
            // 1/7 chance with proper shield
            if (hasWyvernShield(target)) random.nextInt(7) == 0 else Hit.success(this, target, "magic", Item.EMPTY, false)
        }
    }

    // TODO: fix blue ice orb for range as not as it is on runescape the blue ice orb is above his head not his chest

    fun hasWyvernShield(target: Character): Boolean {
        if (target !is Player) return false
        val shieldId = target.equipped(EquipSlot.Shield).id
        return shieldId == "elemental_shield" ||
            shieldId == "mind_shield" ||
            shieldId == "body_shield" ||
            shieldId.startsWith("dragonfire_shield")
    }
}
