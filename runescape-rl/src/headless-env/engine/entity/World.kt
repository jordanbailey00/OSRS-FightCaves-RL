package sim.engine.entity

import com.github.michaelbull.logging.InlineLogger
import org.koin.core.component.KoinComponent
import sim.engine.GameLoop
import sim.engine.client.variable.VariableStore
import sim.engine.client.variable.Variables
import sim.engine.data.ConfigFiles
import sim.engine.data.Settings
import sim.engine.entity.character.npc.loadNpcSpawns
import sim.engine.entity.item.floor.ItemSpawns
import sim.engine.entity.item.floor.loadItemSpawns
import sim.engine.entity.obj.loadObjectSpawns
import sim.engine.get
import sim.engine.timer.TimerQueue
import sim.engine.timer.Timers
import sim.types.Tile
import java.util.concurrent.ConcurrentHashMap

const val MAX_PLAYERS = 0x800 // 2048
const val MAX_NPCS = 0x8000 // 32768

object World : Entity, VariableStore, Runnable, KoinComponent {
    override var tile = Tile.EMPTY

    override val variables = Variables(this)
    private val logger = InlineLogger()

    val members: Boolean
        get() = Settings["world.members", false]

    fun start(files: ConfigFiles) {
        loadItemSpawns(get<ItemSpawns>(), files.list(Settings["spawns.items"]))
        loadObjectSpawns(files.list(Settings["spawns.objects"]))
        loadNpcSpawns(files)
        Spawn.world(files)
    }

    val timers: Timers = TimerQueue(this)

    private val actions = ConcurrentHashMap<String, Pair<Int, () -> Unit>>()

    fun queue(name: String, initialDelay: Int = 0, block: () -> Unit) {
        actions[name] = (GameLoop.tick + initialDelay) to block
    }

    fun containsQueue(name: String) = actions.containsKey(name)

    override fun run() {
        timers.run()
        val iterator = actions.iterator()
        while (iterator.hasNext()) {
            val (_, pair) = iterator.next()
            val (tick, block) = pair
            if (GameLoop.tick <= tick) {
                continue
            }
            iterator.remove()
            try {
                block.invoke()
            } catch (e: Exception) {
                logger.error(e) { "Error in world action!" }
            }
        }
    }

    fun clearQueue(name: String) {
        actions.remove(name)
    }

    fun clear() {
        timers.clearAll()
        for ((_, block) in actions.values) {
            block.invoke()
        }
        actions.clear()
    }

    fun shutdown() {
        Despawn.world()
    }
}
