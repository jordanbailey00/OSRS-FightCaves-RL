package content.skill.melee

import content.skill.melee.weapon.weapon
import sim.engine.Script
import sim.engine.data.definition.AnimationDefinitions
import sim.engine.data.definition.CombatDefinitions
import sim.engine.data.definition.WeaponAnimationDefinitions
import sim.engine.data.definition.WeaponStyleDefinitions
import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.combat.CombatAttack
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.equip.equipped
import sim.engine.entity.character.player.male
import sim.engine.entity.character.sound
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.types.random

class Block(
    val combatDefinitions: CombatDefinitions,
    val styleDefinitions: WeaponStyleDefinitions,
    val weaponDefinitions: WeaponAnimationDefinitions,
    val animationDefinitions: AnimationDefinitions,
) : Script {

    init {
        combatAttack(handler = ::attack)
        npcCombatAttack(handler = ::attack)
    }

    fun attack(source: Character, attack: CombatAttack) {
        val target = attack.target
        val delay = attack.delay
        if (target is Player) {
            source.sound(calculateHitSound(target), delay)
            target.sound(calculateHitSound(target), delay)
            val shield = target.equipped(EquipSlot.Shield).id
            if (shield.endsWith("shield")) {
                target.anim("shield_block", delay)
            } else if (shield.endsWith("defender")) {
                target.anim("defender_block", delay)
            } else if (shield.endsWith("book")) {
                target.anim("book_block", delay)
            } else {
                val type: String? = target.weapon.def.getOrNull("weapon_type")
                val definition = if (type != null) weaponDefinitions.get(type) else null
                var animation = definition?.attackTypes?.get("defend")
                if (animation == null) {
                    val id = target.weapon.def["weapon_style", -1]
                    val style = styleDefinitions.get(id)
                    animation = if (id != -1 && animationDefinitions.contains("${style.stringId}_defend")) "${style.stringId}_defend" else "human_defend"
                }
                target.anim(animation, delay)
            }
        } else if (target is NPC) {
            val id = if (source is Player) target.def(source).stringId else target.id
            val definition = combatDefinitions.get(target.def["combat_def", id])
            target.anim(definition.defendAnim, delay)
            source.sound(definition.defendSound?.id ?: return, delay)
        }
    }

    fun calculateHitSound(target: Character): String {
        if (target is Player) {
            return if (target.male) {
                "male_defend_${random.nextInt(0, 3)}"
            } else {
                "female_defend_${random.nextInt(0, 1)}"
            }
        }
        return "human_defend"
    }
}
