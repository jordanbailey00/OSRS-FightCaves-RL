package content.entity.player

import content.social.trade.exchange.GrandExchange
import kotlinx.coroutines.runBlocking
import sim.engine.Script
import sim.engine.data.SaveQueue
import sim.engine.data.Settings
import sim.engine.entity.World
import sim.engine.entity.character.player.Players
import sim.engine.timer.toTicks
import java.util.concurrent.TimeUnit

class AutoSave(
    val saveQueue: SaveQueue,
    val exchange: GrandExchange,
) : Script {

    init {
        worldSpawn {
            autoSave()
        }

        worldDespawn {
            runBlocking {
                saveQueue.direct().join()
                exchange.save()
            }
        }

        settingsReload {
            val minutes = Settings["storage.autoSave.minutes", 0]
            if (World.contains("auto_save") && minutes <= 0) {
                World.clearQueue("auto_save")
            } else if (!World.contains("auto_save") && minutes > 0) {
                autoSave()
            }
        }
    }

    fun autoSave() {
        val minutes = Settings["storage.autoSave.minutes", 0]
        if (minutes <= 0) {
            return
        }
        World.queue("auto_save", TimeUnit.MINUTES.toTicks(minutes)) {
            for (player in Players) {
                saveQueue.save(player)
            }
            exchange.save()
            autoSave()
        }
    }
}
