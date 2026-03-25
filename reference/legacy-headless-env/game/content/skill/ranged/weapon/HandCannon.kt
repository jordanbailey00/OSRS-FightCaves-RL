package content.skill.ranged.weapon

import content.entity.combat.hit.damage
import content.entity.combat.hit.hit
import content.entity.player.combat.special.specialAttack
import content.entity.proj.shoot
import content.skill.melee.weapon.attackType
import content.skill.melee.weapon.weapon
import content.skill.ranged.ammo
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.equip.equipped
import sim.engine.entity.item.Item
import sim.engine.inv.equipment
import sim.engine.inv.remove
import sim.engine.queue.strongQueue
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.types.random
import kotlin.random.nextInt

class HandCannon : Script {

    init {
        combatSwing("hand_cannon", "range") { target ->
            val ammo = equipped(EquipSlot.Ammo)
            this.ammo = ammo.id
            anim("hand_cannon_shoot")
            gfx("hand_cannon_shoot")
            val time = shoot(id = this.ammo, target = target)
            hit(target, delay = time)
            if (specialAttack) {
                val rapid = attackType == "rapid"
                strongQueue("hit", 2) {
                    anim("hand_cannon_special")
                    gfx("hand_cannon_special")
                    shoot(id = this@combatSwing.ammo, target = target)
                    hit(target, delay = if (rapid) 30 else 60)
                }
            }
            explode(this, if (specialAttack) 0.05 else 0.005)
        }
    }

    fun explode(player: Player, chance: Double) {
        if (random.nextDouble() >= chance || !player.equipment.remove(EquipSlot.Weapon.index, "hand_cannon")) {
            return
        }
        player.anim("hand_cannon_explode")
        player.gfx("hand_cannon_explode")
        player.weapon = Item.EMPTY
        player.damage(random.nextInt(10..160))
        player.message("Your hand cannon explodes!")
    }
}
