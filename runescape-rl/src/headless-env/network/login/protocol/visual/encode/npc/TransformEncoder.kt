package sim.network.login.protocol.visual.encode.npc

import sim.buffer.write.Writer
import sim.network.login.protocol.visual.NPCVisuals
import sim.network.login.protocol.visual.VisualEncoder
import sim.network.login.protocol.visual.VisualMask.TRANSFORM_MASK

class TransformEncoder : VisualEncoder<NPCVisuals>(TRANSFORM_MASK, initial = true) {

    override fun encode(writer: Writer, visuals: NPCVisuals) {
        writer.writeShortAdd(visuals.transform.id)
    }
}
