package content.skill.melee.weapon

import content.area.wilderness.inMultiCombat
import sim.engine.entity.character.Character
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.skill.Skill
import sim.engine.map.spiral

fun multiTargets(target: Character, hits: Int): List<Character> {
    val group = if (target is Player) Players else NPCs
    val targets = mutableListOf<Character>()
    for (tile in target.tile.spiral(1)) {
        val characters = group.at(tile)
        for (character in characters) {
            if (character == target || !character.inMultiCombat) {
                continue
            }
            targets.add(character)
            if (targets.size >= hits) {
                return targets
            }
        }
    }
    return targets
}

fun drainByDamage(target: Character, damage: Int, vararg skills: Skill) {
    if (damage == -1) {
        return
    }
    var drain = damage / 10
    if (drain > 0) {
        for (skill in skills) {
            val current = target.levels.get(skill)
            if (current <= 1) {
                continue
            }
            target.levels.drain(skill, drain)
            drain -= current
            if (drain <= 0) {
                break
            }
        }
    }
}
