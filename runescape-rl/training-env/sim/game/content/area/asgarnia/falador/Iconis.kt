package content.area.asgarnia.falador

import content.entity.player.dialogue.Neutral
import content.entity.player.dialogue.type.npc
import sim.engine.Script
import sim.engine.entity.World
import sim.engine.entity.character.player.Player

class Iconis : Script {

    init {
        npcOperate("Talk-to", "iconis") {
            if (!World.members) {
                nonMember()
                return@npcOperate
            }
        }

        npcOperate("Take-picture", "iconis") {
            if (!World.members) {
                nonMember()
                return@npcOperate
            }
        }
    }

    suspend fun Player.nonMember() {
        npc<Neutral>("Good day! I'm afraid you can't use the booth's services on a non-members world. Film costs a lot you know!")
    }
}
