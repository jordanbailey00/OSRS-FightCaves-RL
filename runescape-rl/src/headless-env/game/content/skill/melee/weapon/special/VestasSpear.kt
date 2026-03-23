package content.skill.melee.weapon.special

import content.area.wilderness.inMultiCombat
import content.entity.combat.Target
import content.entity.combat.hit.hit
import sim.engine.Script
import sim.engine.client.variable.start
import sim.engine.entity.character.CharacterSearch
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.engine.map.spiral

class VestasSpear : Script {

    init {
        specialAttackDamage("spear_wall") { target, _ ->
            start("spear_wall", duration = 8)
            if (!inMultiCombat) {
                return@specialAttackDamage
            }
            var remaining = 15
            val characters: CharacterSearch<*> = if (target is Player) Players else NPCs
            for (tile in tile.spiral(1)) {
                for (char in characters.at(tile)) {
                    if (char == this || char == target || !char.inMultiCombat || !Target.attackable(this, char)) {
                        continue
                    }
                    hit(char)
                    if (--remaining <= 0) {
                        return@specialAttackDamage
                    }
                }
            }
        }
    }
}
