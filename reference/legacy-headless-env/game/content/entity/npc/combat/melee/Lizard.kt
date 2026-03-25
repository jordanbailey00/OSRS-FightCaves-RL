package content.entity.npc.combat.melee

import content.entity.combat.attackers
import content.entity.combat.hit.damage
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.mode.EmptyMode
import sim.engine.entity.character.mode.interact.ItemOnNPCInteract
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.inv.inventory
import sim.engine.inv.remove

class Lizard : Script {

    init {
        itemOnNPCOperate("ice_cooler", "lizard", ::iceCooler)
        itemOnNPCOperate("ice_cooler", "small_lizard*", ::iceCooler)
        itemOnNPCOperate("ice_cooler", "desert_lizard*", ::iceCooler)

        npcLevelChanged(Skill.Constitution, "*lizard", ::killingBlow)
    }

    fun killingBlow(npc: NPC, skill: Skill, from: Int, to: Int) {
        if (to > 10) {
            return
        }
        for (attacker in npc.attackers) {
            attacker.mode = EmptyMode
            if (attacker is Player && attacker["killing_blow", false]) {
                // TODO message
                iceCooler(attacker, npc)
            }
        }
    }

    fun iceCooler(player: Player, interact: ItemOnNPCInteract) {
        iceCooler(player, interact.target)
    }

    fun iceCooler(player: Player, target: NPC) {
        if (!player.inventory.remove("ice_cooler")) {
            return
        }
        player.anim("pie_accurate")
        target.gfx("skip_water_splash")
        val hitpoints = target.levels.get(Skill.Constitution)
        if (hitpoints >= target.levels.getMax(Skill.Constitution) / 4) {
            player.message("The lizard isn't weak enough to be affected by the icy water.")
            return
        }
        player.message("The lizard shudders and collapses from the freezing water.")
        target.damage(hitpoints, source = player)
    }
}
