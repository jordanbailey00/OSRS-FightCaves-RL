package content.social.ignore

import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.isAdmin

fun Player.ignores(other: Player): Boolean = this != other && !other.isAdmin() && ignores.contains(other.accountName)
