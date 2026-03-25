package sim.engine.client.instruction

import sim.engine.entity.character.player.Player
import sim.network.client.Instruction

abstract class InstructionHandler<T : Instruction> {

    /**
     * Validates the [instruction] information is correct and emits a [Player] event with the relevant data
     */
    abstract fun validate(player: Player, instruction: T): Boolean
}
