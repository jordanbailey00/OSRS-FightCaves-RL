package content.area.troll_country.god_wars_dungeon

import content.entity.combat.killer
import sim.engine.Script
import sim.engine.client.instruction.handle.interactNpc
import sim.engine.client.instruction.handle.interactPlayer
import sim.engine.client.ui.close
import sim.engine.client.ui.open
import sim.engine.data.definition.Areas
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.inv.equipment
import sim.types.random

class GodwarsAggression : Script {

    init {
        npcSpawn(handler = ::randomHuntMode)

        entered("godwars_dungeon_multi_area") {
            open("godwars_overlay")
            set("gods", equipment.items.mapNotNull { it.def.getOrNull<String>("god") }.toMutableSet())
        }

        interfaceOpened("godwars_overlay") {
            sendVariable("armadyl_killcount")
            sendVariable("bandos_killcount")
            sendVariable("saradomin_killcount")
            sendVariable("zamorak_killcount")
            sendVariable("godwars_darkness")
        }

        exited("godwars_dungeon_multi_area") {
            close("godwars_overlay")
            if (get("logged_out", false)) {
                return@exited
            }
            set("godwars_darkness", false)
            clear("armadyl_killcount")
            clear("bandos_killcount")
            clear("saradomin_killcount")
            clear("zamorak_killcount")
        }

        itemAdded(inventory = "worn_equipment") { (item) ->
            val god = item.def.getOrNull<String>("god") ?: return@itemAdded
            if (tile in Areas["godwars_dungeon_multi_area"]) {
                get<MutableSet<String>>("gods")!!.add(god)
            }
        }

        itemRemoved(inventory = "worn_equipment") {
            if (tile in Areas["godwars_dungeon_multi_area"]) {
                set("gods", equipment.items.mapNotNull { it.def.getOrNull<String>("god") }.toMutableSet())
            }
        }

        huntPlayer(mode = "godwars_aggressive") { target ->
            interactPlayer(target, "Attack")
        }

        huntNPC(mode = "zamorak_aggressive") { target ->
            interactNpc(target, "Attack")
        }

        huntNPC(mode = "anti_zamorak_aggressive") { target ->
            interactNpc(target, "Attack")
        }

        npcDeath {
            val killer = killer
            if (killer is NPC) {
                randomHuntMode(this)
            } else if (killer is Player) {
                val god = def["god", ""]
                if (god != "") {
                    killer.inc("${god}_killcount")
                }
            }
        }
    }

    fun randomHuntMode(npc: NPC) {
        if (npc.tile in Areas["godwars_dungeon_multi_area"] && (npc.def["hunt_mode", ""] == "zamorak_aggressive" || npc.def["hunt_mode", ""] == "anti_zamorak_aggressive")) {
            npc.huntMode = if (random.nextBoolean()) npc.def["hunt_mode"] else "godwars_aggressive"
        }
    }
}
