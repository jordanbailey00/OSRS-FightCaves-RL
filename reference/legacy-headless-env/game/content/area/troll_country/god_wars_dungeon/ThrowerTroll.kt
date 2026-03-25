package content.area.troll_country.god_wars_dungeon

import sim.engine.Script
import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.combat.CombatDamage
import sim.engine.entity.character.sound

class ThrowerTroll : Script {

    init {
        combatDamage("range", ::attack)
        npcCombatDamage("troll_rock", "range", ::attack)
    }

    fun attack(target: Character, damage: CombatDamage) {
        // TODO need range gfx field
        //  Could potentially rename `type` and have type as the spell/ammo?
        //      TODO Combat vs attack style
        damage.source.sound("troll_rock_defend")
    }
}
