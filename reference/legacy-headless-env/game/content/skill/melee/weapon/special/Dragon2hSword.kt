package content.skill.melee.weapon.special

import content.area.wilderness.inMultiCombat
import content.entity.combat.Target
import content.entity.combat.hit.hit
import sim.engine.Script
import sim.engine.entity.character.CharacterSearch
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.types.Direction

class Dragon2hSword : Script {

    init {
        specialAttackDamage("powerstab") { target, damage ->
            if (!inMultiCombat || damage < 0) {
                return@specialAttackDamage
            }
            val characters: CharacterSearch<*> = if (target is Player) Players else NPCs
            var remaining = if (target is Player) 2 else 14
            for (direction in Direction.reversed) {
                val tile = tile.add(direction)
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
