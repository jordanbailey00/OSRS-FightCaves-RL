package content.entity.obj

import sim.engine.Script
import sim.engine.client.message

class Boxes : Script {

    init {
        objectOperate("Search", "lumbridge_boxes") {
            message("There is nothing interesting in these boxes.")
        }
    }
}
