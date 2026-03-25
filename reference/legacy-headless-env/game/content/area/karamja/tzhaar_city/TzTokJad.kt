package content.area.karamja.tzhaar_city

import content.entity.combat.hit.Damage
import content.entity.combat.hit.hit
import content.entity.combat.target
import sim.engine.Script
import sim.engine.queue.strongQueue

class TzTokJad : Script {

    init {
        npcAttack("tztok_jad", "magic") {
            val target = target ?: return@npcAttack
            // Note: Override for jad only, don't use elsewhere
            strongQueue("hit_target", JAD_HIT_TARGET_QUEUE_TICKS) {
                val resolvedDamage = hit(target, offensiveType = "magic", delay = JAD_HIT_CLIENT_DELAY, damage = Damage.roll(this@npcAttack, target, offensiveType = "magic", range = 0..950))
                recordJadAttackOutcome(target, resolvedDamage)
            }
        }

        npcAttack("tztok_jad", "range") {
            val target = target ?: return@npcAttack
            // Note: Override for jad only, don't use elsewhere
            strongQueue("hit_target", JAD_HIT_TARGET_QUEUE_TICKS) {
                val resolvedDamage = hit(target, offensiveType = "range", delay = JAD_HIT_CLIENT_DELAY, damage = Damage.roll(this@npcAttack, target, offensiveType = "range", range = 0..970))
                recordJadAttackOutcome(target, resolvedDamage)
            }
        }
    }
}
