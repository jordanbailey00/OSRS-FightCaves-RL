package content.skill.melee.weapon.special

import content.entity.effect.freeze
import sim.engine.Script
import sim.engine.timer.toTicks
import java.util.concurrent.TimeUnit

class ZamorakGodsword : Script {

    init {
        specialAttackDamage("ice_cleave") { target, damage ->
            if (damage < 0) {
                return@specialAttackDamage
            }
            freeze(target, TimeUnit.SECONDS.toTicks(20))
        }
    }
}
