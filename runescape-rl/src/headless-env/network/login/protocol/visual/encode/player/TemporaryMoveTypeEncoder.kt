package sim.network.login.protocol.visual.encode.player

import sim.buffer.write.Writer
import sim.network.login.protocol.visual.PlayerVisuals
import sim.network.login.protocol.visual.VisualEncoder
import sim.network.login.protocol.visual.VisualMask.TEMPORARY_MOVEMENT_TYPE_MASK

class TemporaryMoveTypeEncoder : VisualEncoder<PlayerVisuals>(TEMPORARY_MOVEMENT_TYPE_MASK, initial = true) {

    override fun encode(writer: Writer, visuals: PlayerVisuals) {
        writer.writeByteInverse(visuals.temporaryMoveType.type.id)
    }
}
