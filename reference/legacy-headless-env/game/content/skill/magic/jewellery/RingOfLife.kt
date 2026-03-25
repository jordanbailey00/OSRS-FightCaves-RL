package content.skill.magic.jewellery

import content.area.wilderness.inWilderness
import content.area.wilderness.wildernessLevel
import content.skill.magic.book.modern.teleBlocked
import sim.engine.Script
import sim.engine.data.Settings
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.equip.equipped
import sim.engine.entity.character.player.skill.Skill
import sim.engine.inv.discharge
import sim.engine.inv.equipment
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.types.Tile

class RingOfLife : Script {

    init {
        levelChanged(Skill.Constitution) { skill, from, to ->
            if (to >= from || to < 1) {
                return@levelChanged
            }
            if (equipped(EquipSlot.Ring).id != "ring_of_life") {
                return@levelChanged
            }
            if (equipped(EquipSlot.Amulet).id == "phoenix_necklace") {
                return@levelChanged
            }
            val maxHp = levels.getMax(skill)
            val threshold = maxHp / 10
            if (to > threshold) {
                return@levelChanged
            }
            if (inWilderness && wildernessLevel >= 30) {
                return@levelChanged
            }
            if (teleBlocked) {
                return@levelChanged
            }
            activateRingOfLife(this)
        }
    }

    private fun activateRingOfLife(player: Player) {
        if (!player.equipment.discharge(player, EquipSlot.Ring.index)) {
            return
        }
        val destination = player["respawn_tile", Tile(Settings["world.home.x", 0], Settings["world.home.y", 0])]
        itemTeleport(player, destination, "jewellery")
    }
}
