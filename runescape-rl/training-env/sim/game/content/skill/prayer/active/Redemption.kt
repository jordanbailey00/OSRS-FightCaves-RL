package content.skill.prayer.active

import content.skill.prayer.praying
import sim.engine.Script
import sim.engine.entity.character.player.equip.equipped
import sim.engine.entity.character.player.skill.Skill
import sim.network.login.protocol.visual.update.player.EquipSlot

class Redemption : Script {

    init {
        levelChanged(Skill.Constitution) { skill, _, to ->
            if (to <= 0 || to >= levels.getMax(skill) / 10 || !praying("redemption")) {
                return@levelChanged
            }
            if (equipped(EquipSlot.Amulet).id == "phoenix_necklace") {
                return@levelChanged
            }
            levels.set(Skill.Prayer, 0)
            val health = (levels.getMax(Skill.Prayer) * 2.5).toInt()
            levels.restore(Skill.Constitution, health)
            gfx("redemption")
        }
    }
}
