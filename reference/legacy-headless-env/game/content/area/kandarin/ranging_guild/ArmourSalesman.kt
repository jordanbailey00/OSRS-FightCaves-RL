package content.area.kandarin.ranging_guild

import content.entity.npc.shop.openShop
import sim.engine.Script
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level

class ArmourSalesman : Script {

    init {
        npcOperate("Trade", "armour_salesman") {
            when {
                Skill.all.any { it != Skill.Ranged && levels.getMax(it) == Level.MAX_LEVEL } -> {
                    openShop("aarons_archery_appendages_trimmed")
                }
                levels.getMax(Skill.Ranged) == Level.MAX_LEVEL -> {
                    openShop("aarons_archery_appendages_skillcape")
                }
                else -> {
                    openShop("aarons_archery_appendages")
                }
            }
        }
    }
}
