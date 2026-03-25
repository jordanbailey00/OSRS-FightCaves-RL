package sim.engine.event

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import sim.engine.GameLoop
import sim.engine.data.Settings
import sim.engine.entity.Entity
import sim.engine.entity.World
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item
import sim.engine.entity.item.floor.FloorItem
import sim.engine.entity.obj.GameObject
import sim.types.Tile
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Logger for storing a record of import game events
 * Useful for auditing, investigations, and adventurer logs
 * Note: not thread safe; only use within game thread
 */
object AuditLog {
    private const val LOG_BUFFER_SIZE = 8192
    val logs = ObjectArrayList<String>(LOG_BUFFER_SIZE)
    val ISO_LOCAL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")

    fun event(source: Entity, action: String, vararg context: Any?) {
        add {
            append(ref(source)).append("\t")
            append(action.uppercase())
            for (info in context) {
                append("\t").append(ref(info))
            }
        }
    }

    private fun ref(source: Any?) = when (source) {
        is Player -> "PLAYER ${source.accountName}"
        is NPC -> "NPC ${source.id}:${source.index}"
        is FloorItem -> "FLOOR_ITEM ${source.id}:${source.amount}"
        is Item -> "ITEM ${source.id}:${source.value}"
        is GameObject -> "OBJECT ${source.id}"
        is World -> "WORLD 1"
        is Tile -> "TILE ${source.x},${source.y},${source.level}"
        else -> source.toString()
    }

    fun info(message: String) {
        add {
            append("SYSTEM").append("\t")
            append(message)
        }
    }

    private fun add(block: StringBuilder.() -> Unit) {
        logs.add(buildString {
            append(System.currentTimeMillis()).append("\t")
            append(GameLoop.tick).append("\t")
            block()
        })
    }

    fun save(directory: File = File(Settings["storage.players.logs"]), now: LocalDateTime = LocalDateTime.now()) {
        if (logs.isEmpty) {
            return
        }
        val hourTime = now.withMinute(0).withSecond(0).withNano(0)
        val file = directory.resolve("${ISO_LOCAL_FORMAT.format(hourTime)}.tsv")
        file.appendText(buildString {
            for (log in logs) {
                appendLine(log)
            }
        })
        logs.clear()
    }

}
