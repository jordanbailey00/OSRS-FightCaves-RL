package sim.network.login.protocol.visual.encode.npc

import sim.buffer.write.Writer
import sim.network.login.protocol.visual.NPCVisuals
import sim.network.login.protocol.visual.VisualEncoder
import sim.network.login.protocol.visual.VisualMask.NPC_FACE_MASK

class NPCFaceEncoder : VisualEncoder<NPCVisuals>(NPC_FACE_MASK, initial = true) {

    override fun encode(writer: Writer, visuals: NPCVisuals) {
        val (targetX, targetY) = visuals.face
        writer.apply {
            writeShortAdd(targetX * 2 + 1)
            writeShortLittle(targetY * 2 + 1)
        }
    }
}
