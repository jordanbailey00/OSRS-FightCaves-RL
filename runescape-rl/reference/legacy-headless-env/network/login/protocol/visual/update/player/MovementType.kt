package sim.network.login.protocol.visual.update.player

import sim.network.login.protocol.Visual

data class MovementType(var type: MoveType = MoveType.None) : Visual {
    override fun needsReset(): Boolean = type != MoveType.None

    override fun reset() {
        type = MoveType.None
    }
}
