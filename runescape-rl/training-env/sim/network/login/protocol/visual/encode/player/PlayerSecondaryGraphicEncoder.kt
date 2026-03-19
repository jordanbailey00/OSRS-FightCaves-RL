package sim.network.login.protocol.visual.encode.player

import sim.buffer.write.Writer
import sim.network.login.protocol.visual.PlayerVisuals
import sim.network.login.protocol.visual.VisualEncoder
import sim.network.login.protocol.visual.VisualMask.PLAYER_GRAPHIC_2_MASK

class PlayerSecondaryGraphicEncoder : VisualEncoder<PlayerVisuals>(PLAYER_GRAPHIC_2_MASK) {

    override fun encode(writer: Writer, visuals: PlayerVisuals) {
        val visual = visuals.secondaryGraphic
        writer.apply {
            writeShortAddLittle(visual.id)
            writeIntLittle(visual.packedDelayHeight)
            writeByteSubtract(visual.packedRotationRefresh)
        }
    }
}
