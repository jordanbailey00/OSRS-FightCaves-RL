package content.area.kandarin.ourania

import sim.engine.Script
import sim.engine.client.ui.open
import sim.engine.entity.character.move.tele

class OuraniaCrack : Script {

    init {
        objectOperate("Squeeze-through", "ourania_crack_enter") {
            open("fade_out")
            delay(3)
            tele(3312, 4817)
            delay(1)
            open("fade_in")
        }

        objectOperate("Squeeze-through", "ourania_crack_exit") {
            open("fade_out")
            delay(3)
            tele(3308, 4819)
            delay(1)
            open("fade_in")
        }
    }
}
