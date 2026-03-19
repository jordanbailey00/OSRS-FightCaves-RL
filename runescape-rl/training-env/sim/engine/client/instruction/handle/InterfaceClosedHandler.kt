package sim.engine.client.instruction.handle

import sim.engine.client.instruction.InstructionHandler
import sim.engine.entity.character.player.Player
import sim.network.client.instruction.InterfaceClosedInstruction

class InterfaceClosedHandler : InstructionHandler<InterfaceClosedInstruction>() {

    override fun validate(player: Player, instruction: InterfaceClosedInstruction): Boolean {
        val id = player.interfaces.get("main_screen") ?: player.interfaces.get("wide_screen") ?: player.interfaces.get("underlay")
        if (id != null) {
            player.interfaces.close(id)
            return true
        }
        return false
    }
}
