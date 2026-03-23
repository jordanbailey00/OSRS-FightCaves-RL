import com.github.michaelbull.logging.InlineLogger
import content.area.karamja.tzhaar_city.FightCaveDemoObservability
import world.gregs.voidps.engine.data.AccountManager
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.event.AuditLog
import world.gregs.voidps.engine.get

const val DEFAULT_PROPERTY_FILE = "game.properties"
const val FIGHT_CAVES_DEMO_PROPERTY_FILE = "fight-caves-demo.properties"

data class GameProfile(
    val id: String,
    val propertyFiles: List<String> = listOf(DEFAULT_PROPERTY_FILE),
    val afterPreload: () -> Unit = {},
)

object GameProfiles {
    val default = GameProfile(id = "default")
    val fightCavesDemo = GameProfile(
        id = "fight-caves-demo",
        propertyFiles = listOf(DEFAULT_PROPERTY_FILE, FIGHT_CAVES_DEMO_PROPERTY_FILE),
        afterPreload = FightCavesDemoBootstrap::install,
    )
}

object FightCavesDemoBootstrap {
    private val logger = InlineLogger()
    private var installed = false

    fun install() {
        if (installed) {
            return
        }
        installed = true

        val accountManager: AccountManager = get()
        val existing = accountManager.loadCallback
        accountManager.loadCallback = { player ->
            markDemoLogin(player)
            existing(player)
        }
        AuditLog.info("fight_cave_demo_bootstrap_installed")
        logger.info { "Fight Caves demo bootstrap installed." }
    }

    private fun markDemoLogin(player: Player) {
        player["fight_cave_demo_profile"] = true
        player["fight_cave_demo_entry_pending"] = true
        FightCaveDemoObservability.beginSession(player)
        AuditLog.event(player, "fight_cave_demo_profile_login", player.tile)
    }
}
