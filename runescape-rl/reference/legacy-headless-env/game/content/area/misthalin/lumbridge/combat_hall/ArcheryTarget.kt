package content.area.misthalin.lumbridge.combat_hall

import content.entity.combat.hit.Damage
import content.entity.combat.underAttack
import content.entity.proj.shoot
import content.skill.melee.weapon.fightStyle
import content.skill.melee.weapon.weapon
import content.skill.ranged.ammo
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.closeDialogue
import sim.engine.client.variable.remaining
import sim.engine.client.variable.start
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.equip.equipped
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.exp.exp
import sim.engine.entity.character.player.skill.level.Interpolation
import sim.engine.entity.obj.GameObject
import sim.engine.inv.equipment
import sim.engine.inv.remove
import sim.engine.queue.weakQueue
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.types.random

class ArcheryTarget : Script {

    init {
        objectOperate("Shoot-at", "archery_target") { (target) ->
            closeDialogue()
            face(target)
            swing(this, target, 0)
        }
    }

    fun swing(player: Player, obj: GameObject, delay: Int) {
        player.weakQueue("archery", delay) {
            val weapon = player.weapon
            if (player.fightStyle != "range") {
                player.message("You can only use Ranged against this target.")
                return@weakQueue
            }
            if (weapon.id != "training_bow") {
                player.message("You can only use a Training bow and arrows against this target.")
                return@weakQueue
            }
            if (player.underAttack) {
                player.message("You are already in combat.")
                return@weakQueue
            }
            player.ammo = ""
            val ammo = player.equipped(EquipSlot.Ammo)
            if (ammo.isNotEmpty() && ammo.id != "training_arrows") {
                player.message("You can't use that ammo with your bow.")
                return@weakQueue
            }
            if (ammo.isEmpty()) {
                player.message("There is no ammo left in your quiver.")
                return@weakQueue
            }
            val remaining = player.remaining("action_delay")
            if (remaining <= 0) {
                player.ammo = "training_arrows"
                player.equipment.remove(player.ammo)
                player.face(obj)
                player.anim("bow_accurate")
                player.gfx("training_arrows_shoot")
                // We're going to ignore success check as we have no [Character] to check against
                val maxHit = Damage.maximum(player, player, "range", weapon)
                val hit = random.nextInt(-1, maxHit + 1)
                val height = Interpolation.lerp(hit, -1..maxHit, 0..20)
                player.shoot(id = player.ammo, obj.tile, endHeight = height)
                if (hit != -1) {
                    player.exp(Skill.Ranged, hit / 2.5)
                }
                if (ammo.amount == 1) {
                    player.message("That was your last one!")
                }
                val attackDelay = weapon.def["attack_speed", 4]
                player.start("action_delay", attackDelay)
                swing(player, obj, attackDelay)
            } else {
                swing(player, obj, remaining)
            }
        }
    }
}
