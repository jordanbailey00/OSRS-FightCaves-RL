package sim.network.login.protocol.visual.encode.npc

import sim.buffer.write.Writer
import sim.network.login.protocol.visual.NPCVisuals
import sim.network.login.protocol.visual.VisualEncoder
import sim.network.login.protocol.visual.VisualMask.NPC_GRAPHIC_2_MASK

class NPCSecondaryGraphicEncoder : VisualEncoder<NPCVisuals>(NPC_GRAPHIC_2_MASK) {

    override fun encode(writer: Writer, visuals: NPCVisuals) {
        val visual = visuals.secondaryGraphic
        writer.apply {
            writeShortLittle(visual.id)
            writeIntLittle(visual.packedDelayHeight)
            writeByteSubtract(visual.packedRotationRefresh)
        }
    }
}
