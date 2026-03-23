package sim.network.login.protocol.visual

import sim.network.login.protocol.visual.update.npc.Transformation

class NPCVisuals : Visuals() {

    val transform = Transformation()

    override fun reset() {
        super.reset()
        transform.clear()
    }
}
