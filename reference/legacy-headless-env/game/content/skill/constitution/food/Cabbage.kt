package content.skill.constitution.food

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.chat.ChatType

class Cabbage : Script {

    init {
        consumed("cabbage") { _, _ ->
            message("You don't really like it much.", ChatType.Filter)
        }
    }
}
