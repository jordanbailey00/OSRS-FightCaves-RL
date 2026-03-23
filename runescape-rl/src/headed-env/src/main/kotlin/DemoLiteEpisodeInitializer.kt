import world.gregs.voidps.engine.data.AccountManager
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.get
import world.gregs.voidps.type.Tile

data class DemoLiteEpisodeContext(
    val player: Player,
    val episodeConfig: FightCaveEpisodeConfig,
    val episodeState: FightCaveEpisodeState,
    val resetCount: Int,
)

class DemoLiteEpisodeInitializer(
    private val runtime: OracleRuntime,
    private val config: DemoLiteConfig,
) {
    private val accounts: AccountManager = get()
    private var player: Player? = null
    private var resetCount: Int = 0

    fun resetWaveOne(): DemoLiteEpisodeContext {
        val currentPlayer = player ?: createPlayer("${config.accountNamePrefix}-${System.nanoTime()}").also {
            player = it
        }
        resetCount += 1
        val episodeConfig =
            FightCaveEpisodeConfig(
                seed = config.seedBase + resetCount,
                startWave = config.startWave,
            )
        val episodeState = runtime.resetFightCaveEpisode(currentPlayer, episodeConfig)
        return DemoLiteEpisodeContext(
            player = currentPlayer,
            episodeConfig = episodeConfig,
            episodeState = episodeState,
            resetCount = resetCount,
        )
    }

    private fun createPlayer(name: String, tile: Tile = Tile(2438, 5168)): Player {
        val player = Player(tile = tile, accountName = name, passwordHash = "")
        check(accounts.setup(player, null, 0, viewport = true)) {
            "Failed to setup demo-lite player '$name'."
        }
        player["creation"] = -1
        player["skip_level_up"] = true
        player["fight_cave_wave"] = -1
        player["fight_caves_logout_warning"] = false
        player["logged_out"] = false
        accounts.spawn(player, null)
        player.queue.clear()
        player.softTimers.stopAll()
        player.timers.stopAll()
        player.clear("fight_cave_wave")
        player.clear("fight_cave_rotation")
        player.clear("fight_cave_remaining")
        player["fight_caves_logout_warning"] = false
        player["logged_out"] = false
        player.viewport?.loaded = true
        return player
    }
}
