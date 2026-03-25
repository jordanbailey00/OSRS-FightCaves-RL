package content.entity.obj.door

import sim.engine.Script

class Doors : Script {

    init {
        objectOperate("Close") { (target) ->
            closeDoor(target)
        }

        objectOperate("Open") { (target) ->
            openDoor(target)
        }
    }
}
