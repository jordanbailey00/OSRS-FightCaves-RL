package content.area.troll_country.god_wars_dungeon

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.mode.interact.ItemOnObjectInteract
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level.has
import sim.engine.inv.inventory
import sim.engine.inv.remove

class SaradominRock : Script {

    init {
        entered("godwars_dungeon_multi_area") {
            sendVariable("godwars_saradomin_rope_top")
            sendVariable("godwars_saradomin_rope_bottom")
        }

        objectOperate("Tie-rope", "godwars_saradomin_rock_top,godwars_saradomin_rock_bottom") { (target) ->
            tieRope(this, target.def(this).stringId)
        }

        itemOnObjectOperate("rope", "godwars_saradomin_rock_top", handler = ::tieRope)
        itemOnObjectOperate("rope", "godwars_saradomin_rock_bottom", handler = ::tieRope)
    }

    fun tieRope(player: Player, interact: ItemOnObjectInteract) {
        tieRope(player, interact.target.def.stringId)
    }

    fun tieRope(player: Player, id: String) {
        if (!player.has(Skill.Agility, 70, message = true)) {
            return
        }
        if (!player.inventory.remove("rope")) {
            player.message("You aren't carrying a rope with you.")
            return
        }
        player.anim("climb_up")
        player[id.replace("rock", "rope")] = true
    }
}
