package sim.network.login.protocol.visual.encode.player

import sim.buffer.write.Writer
import sim.network.login.protocol.visual.PlayerVisuals
import sim.network.login.protocol.visual.VisualEncoder
import sim.network.login.protocol.visual.VisualMask.PLAYER_GRAPHIC_1_MASK

class PlayerPrimaryGraphicEncoder : VisualEncoder<PlayerVisuals>(PLAYER_GRAPHIC_1_MASK) {

    override fun encode(writer: Writer, visuals: PlayerVisuals) {
        val visual = visuals.primaryGraphic
        writer.apply {
            writeShortAddLittle(visual.id)
            writeIntInverseMiddle(visual.packedDelayHeight)
            writeByteAdd(visual.packedRotationRefresh)
        }
    }
}
