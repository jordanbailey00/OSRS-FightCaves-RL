package sim.engine.entity.character.player.chat

import sim.engine.client.message
import sim.engine.entity.character.Character

fun Character.cantReach() = message("I can't reach that.", ChatType.Engine)

fun Character.inventoryFull(to: String = "") = notEnough("inventory space${if (to.isNotBlank()) " $to" else ""}")

fun Character.noInterest() = message("Nothing interesting happens.", ChatType.Engine)

fun Character.notEnough(thing: String) = message("You don't have enough $thing.")

fun Character.obstacle(level: Int) = message("You need an Agility level of $level to use this obstacle.")
