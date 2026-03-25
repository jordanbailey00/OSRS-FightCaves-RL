package content.entity.player.modal

import net.pearx.kasechange.toSnakeCase
import net.pearx.kasechange.toTitleCase
import sim.engine.Script
import sim.engine.client.instruction.instruction
import sim.engine.client.ui.hasOpen
import sim.engine.client.ui.open
import sim.engine.entity.character.player.Player
import sim.engine.queue.weakQueue
import sim.network.client.instruction.ChangeDisplayMode

class GameFrame : Script {

    val list = listOf(
        "chat_box",
        "chat_background",
        "filter_buttons",
        "private_chat",
        "health_orb",
        "prayer_orb",
        "energy_orb",
        "summoning_orb",
        "combat_styles",
        "task_system",
        "task_popup",
        "stats",
        "quest_journals",
        "inventory",
        "worn_equipment",
        "prayer_list",
        "modern_spellbook",
        "friends_list",
        "ignore_list",
        "clan_chat",
        "options",
        "emotes",
        "music_player",
        "notes",
        "area_status_icon",
    )

    init {
        Tab.entries.forEach { tab ->
            val name = tab.name.toSnakeCase()
            interfaceOption(name.toTitleCase(), "toplevel*:$name") {
                set("tab", false, tab.name)
            }
        }

        instruction<ChangeDisplayMode> { player ->
            if (player.interfaces.displayMode == displayMode || !player.hasOpen("graphics_options")) {
                return@instruction
            }
            player.interfaces.setDisplayMode(displayMode)
        }

        interfaceOpened("toplevel*") {
            openGamframe(this)
        }

        interfaceRefresh("toplevel*,dialogue_npc*") {
            interfaces.sendVisibility(interfaces.gameFrame, "wilderness_level", false)
            weakQueue("wild_level", 1, onCancel = null) {
                interfaces.sendVisibility(interfaces.gameFrame, "wilderness_level", false)
            }
        }
    }

    fun GameFrame.openGamframe(player: Player) {
        for (name in list) {
            if (name.endsWith("_spellbook")) {
                val book = player["spellbook_config", 0] and 0x3
                player.open(
                    when (book) {
                        1 -> "ancient_spellbook"
                        2 -> "lunar_spellbook"
                        3 -> "dungeoneering_spellbook"
                        else -> name
                    },
                )
            } else {
                player.open(name)
            }
        }
    }
}
