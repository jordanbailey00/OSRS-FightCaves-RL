package content.area.misthalin.lumbridge.combat_hall

import content.entity.combat.attackers
import content.skill.melee.weapon.fightStyle
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.mode.EmptyMode
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.skill.Skill

class CombatDummy : Script {

    init {
        npcLevelChanged(Skill.Constitution, "melee_dummy,magic_dummy") { _, _, to ->
            if (to <= 10) {
                levels.clear()
                for (attacker in attackers) {
                    attacker.mode = EmptyMode
                }
            }
        }

        combatPrepare { target ->
            when {
                target is NPC && target.id == "magic_dummy" && fightStyle != "magic" -> {
                    message("You can only use Magic against this dummy.")
                    false
                }
                target is NPC && target.id == "melee_dummy" && fightStyle != "melee" -> {
                    message("You can only use Melee against this dummy.")
                    false
                }
                else -> true
            }
        }
    }
}
