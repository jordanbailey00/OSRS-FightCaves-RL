package content.skill.agility.course

import sim.engine.entity.character.npc.NPCs
import sim.types.Zone
import sim.types.random

internal fun NPCs.gnomeTrainer(message: String, zone: Zone) {
    val trainer = at(zone).randomOrNull(random) ?: return
    trainer.say(message)
}

internal fun NPCs.gnomeTrainer(message: String, zones: List<Zone>) {
    for (zone in zones) {
        val trainer = at(zone).randomOrNull(random) ?: continue
        trainer.say(message)
        break
    }
}
