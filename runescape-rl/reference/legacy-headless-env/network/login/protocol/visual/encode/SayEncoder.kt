package sim.network.login.protocol.visual.encode

import sim.buffer.write.Writer
import sim.network.login.protocol.visual.VisualEncoder
import sim.network.login.protocol.visual.Visuals

class SayEncoder(mask: Int) : VisualEncoder<Visuals>(mask) {

    override fun encode(writer: Writer, visuals: Visuals) {
        writer.writeString(visuals.say.text)
    }
}
