package sim.network.login.protocol.visual.encode.npc

import sim.buffer.write.Writer
import sim.network.login.protocol.visual.NPCVisuals
import sim.network.login.protocol.visual.VisualEncoder
import sim.network.login.protocol.visual.VisualMask.NPC_GRAPHIC_1_MASK

class NPCPrimaryGraphicEncoder : VisualEncoder<NPCVisuals>(NPC_GRAPHIC_1_MASK) {

    override fun encode(writer: Writer, visuals: NPCVisuals) {
        val visual = visuals.primaryGraphic
        writer.apply {
            writeShortLittle(visual.id)
            writeIntMiddle(visual.packedDelayHeight)
            writeByte(visual.packedRotationRefresh)
        }
    }
}
