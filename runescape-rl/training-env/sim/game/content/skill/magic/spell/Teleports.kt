package content.skill.magic.spell

import content.quest.quest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.ItemOption
import sim.engine.client.ui.chat.plural
import sim.engine.client.ui.closeInterfaces
import sim.engine.client.variable.hasClock
import sim.engine.client.variable.remaining
import sim.engine.client.variable.start
import sim.engine.data.definition.Areas
import sim.engine.data.definition.SpellDefinitions
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.exp.exp
import sim.engine.entity.character.sound
import sim.engine.inv.inventory
import sim.engine.inv.remove
import sim.engine.map.collision.random
import sim.engine.queue.queue
import sim.engine.queue.weakQueue
import sim.engine.timer.epochSeconds
import java.util.concurrent.TimeUnit

class Teleports(val definitions: SpellDefinitions) : Script {

    init {
        interfaceOption("Cast", "*_spellbook:*_teleport") {
            val component = it.component
            if (component != "lumbridge_home_teleport") {
                cast(it.id, it.component)
                return@interfaceOption
            }
            val seconds = remaining("home_teleport_timeout", epochSeconds())
            if (seconds > 0) {
                val remaining = TimeUnit.SECONDS.toMinutes(seconds.toLong())
                message("You have to wait $remaining ${"minute".plural(remaining)} before trying this again.")
                return@interfaceOption
            }
            if (hasClock("teleport_delay")) {
                return@interfaceOption
            }
            weakQueue("home_teleport") {
                if (!removeSpellItems(component)) {
                    cancel()
                    return@weakQueue
                }
                onCancel = {
                    start("teleport_delay", 1)
                }
                start("teleport_delay", 17)
                repeat(17) {
                    gfx("home_tele_${it + 1}")
                    val ticks = anim("home_tele_${it + 1}")
                    pause(ticks)
                }
                withContext(NonCancellable) {
                    tele(Areas["lumbridge_teleport"].random())
                    set("click_your_heels_three_times_task", true)
                    start("home_teleport_timeout", TimeUnit.MINUTES.toSeconds(30).toInt(), epochSeconds())
                }
            }
        }

        interfaceOption("Cast", "*_spellbook:ardougne_teleport") {
            if (quest("plague_city") != "completed_with_spell") {
                message("You haven't learnt how to cast this spell yet.")
                return@interfaceOption
            } else {
                cast(it.id, it.component)
            }
        }

        itemOption("Read", "*_teleport", handler = ::teleport)
        itemOption("Break", "*_teleport", handler = ::teleport)
    }

    fun teleport(player: Player, option: ItemOption) {
        if (player.contains("delay") || player.queue.contains("teleport")) {
            return
        }
        player.closeInterfaces()
        val definition = Areas.getOrNull(option.item.id) ?: return
        val scrolls = Areas.tagged("scroll")
        val type = if (scrolls.contains(definition)) "scroll" else "tablet"
        val map = definition.area
        player.queue("teleport", onCancel = null) {
            if (player.inventory.remove(option.item.id)) {
                player.sound("teleport_$type")
                player.gfx("teleport_$type")
                player.anim("teleport_$type")
                player.delay(3)
                player.tele(map.random(player)!!)
                player.animDelay("teleport_land")
            }
        }
    }

    fun Player.cast(id: String, component: String) {
        if (contains("delay") || queue.contains("teleport")) {
            return
        }
        closeInterfaces()
        queue("teleport", onCancel = null) {
            if (!removeSpellItems(component)) {
                return@queue
            }
            val definition = definitions.get(component)
            exp(Skill.Magic, definition.experience)
            val book = id.removeSuffix("_spellbook")
            sound("teleport")
            gfx("teleport_$book")
            animDelay("teleport_$book")
            tele(Areas[component].random(player)!!)
            delay(1)
            sound("teleport_land")
            gfx("teleport_land_$book")
            animDelay("teleport_land_$book")
            if (book == "ancient") {
                delay(1)
                clearAnim()
            }
        }
    }
}
