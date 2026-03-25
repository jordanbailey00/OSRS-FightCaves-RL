package sim.network.login.protocol.visual.encode.player

import sim.buffer.write.Writer
import sim.network.login.protocol.visual.PlayerVisuals
import sim.network.login.protocol.visual.VisualEncoder
import sim.network.login.protocol.visual.VisualMask.PLAYER_FACE_MASK

class PlayerFaceEncoder : VisualEncoder<PlayerVisuals>(PLAYER_FACE_MASK, initial = true) {

    override fun encode(writer: Writer, visuals: PlayerVisuals) {
        writer.writeShort(visuals.face.direction)
    }
}
