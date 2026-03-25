package content.area.asgarnia.entrana

import content.entity.effect.stun
import sim.engine.Script
import sim.engine.client.message
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.chat.inventoryFull
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.exp.exp
import sim.engine.entity.character.player.skill.level.Level
import sim.engine.entity.character.sound
import sim.engine.inv.add
import sim.engine.inv.inventory
import sim.types.Direction
import sim.types.Tile

class Entrana : Script {

    init {
        objectOperate("Cross", "entrana_gangplank_exit") {
            walkOverDelay(Tile(2834, 3333, 1))
            tele(2834, 3335, 0)
        }

        objectOperate("Cross", "gangplank_entrana_enter") {
            walkOverDelay(Tile(2834, 3334))
            tele(2834, 3332, 1)
        }

        objectOperate("Steal", "candles_entrana") {
            if (!Level.success(levels.get(Skill.Thieving), 25..160)) { // Unknown rate
                stun(this, 8, 100)
                message("A higher power smites you.")
                walkTo(tile.add(Direction.SOUTH_WEST))
                return@objectOperate
            }
            if (!inventory.add("white_candle")) {
                inventoryFull()
                return@objectOperate
            }
            exp(Skill.Thieving, 20.0)
            sound("pick")
            anim("take")
            message("You steal a candle.", ChatType.Filter)
        }
    }
}
