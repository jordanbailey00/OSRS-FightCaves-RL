package content.entity.player.modal.tab

import sim.engine.Script

class Notes : Script {

    init {
        interfaceOpened("notes") { id ->
            interfaceOptions.unlockAll(id, "notes", 0..30)
        }
    }
}
