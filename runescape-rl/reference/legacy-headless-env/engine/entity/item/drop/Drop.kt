package sim.engine.entity.item.drop

import sim.engine.entity.character.player.Player

interface Drop {
    val chance: Int
    val predicate: ((Player) -> Boolean)?
}
