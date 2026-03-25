package content.area.troll_country.god_wars_dungeon.zamorak

import content.skill.prayer.protectMelee
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.npc.NPC

class Gorak : Script {

    init {
        combatDamage { (source) ->
            if (source is NPC && source.id.startsWith("gorak") && protectMelee()) {
                message("Your protective prayer doesn't seem to work!")
            }
        }
    }
}
