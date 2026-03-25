package content.skill.constitution.food

import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.male

class Onion : Script {

    init {
        consumed("onion") { _, _ ->
            message("It hurts to see a grown ${if (male) "male" else "female"} cry.", ChatType.Filter)
        }
    }
}
