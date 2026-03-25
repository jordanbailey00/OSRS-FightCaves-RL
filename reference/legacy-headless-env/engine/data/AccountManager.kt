package sim.engine.data

import sim.engine.client.message
import sim.engine.client.ui.InterfaceOptions
import sim.engine.client.ui.Interfaces
import sim.engine.client.ui.open
import sim.engine.client.update.view.Viewport
import sim.engine.client.variable.PlayerVariables
import sim.engine.data.definition.*
import sim.engine.entity.Despawn
import sim.engine.entity.Spawn
import sim.engine.entity.World
import sim.engine.entity.character.mode.move.Moved
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.appearance
import sim.engine.entity.character.player.equip.AppearanceOverrides
import sim.engine.entity.character.player.name
import sim.engine.entity.character.player.skill.level.PlayerLevels
import sim.engine.event.AuditLog
import sim.engine.inv.equipment
import sim.engine.inv.restrict.ValidItemRestriction
import sim.engine.inv.stack.ItemDependentStack
import sim.engine.map.collision.CollisionStrategyProvider
import sim.engine.queue.strongQueue
import sim.network.client.Client
import sim.network.client.ConnectionQueue
import sim.network.login.protocol.encode.logout
import sim.types.Delta
import sim.types.Direction
import sim.types.Tile

class AccountManager(
    private val accountDefinitions: AccountDefinitions,
    private val variableDefinitions: VariableDefinitions,
    private val saveQueue: SaveQueue,
    private val connectionQueue: ConnectionQueue,
    private val overrides: AppearanceOverrides,
) {
    private val validItems = ValidItemRestriction()
    private val homeTile: Tile
        get() = Tile(Settings["world.home.x", 0], Settings["world.home.y", 0], Settings["world.home.level", 0])

    fun create(name: String, passwordHash: String): Player = Player(tile = homeTile, accountName = name, passwordHash = passwordHash).apply {
        this["creation"] = System.currentTimeMillis()
        this["new_player"] = true
    }

    fun setup(player: Player, client: Client?, displayMode: Int, viewport: Boolean = true): Boolean {
        player.index = Players.index() ?: return false
        player.visuals.hits.self = player.index
        player.interfaces = Interfaces(player)
        player.interfaceOptions = InterfaceOptions(player)
        (player.variables as PlayerVariables).definitions = variableDefinitions
//        player.area.areaDefinitions = areaDefinitions
        player.inventories.validItemRule = validItems
        player.inventories.normalStack = ItemDependentStack
        player.inventories.player = player
        player.inventories.start()
        player.steps.previous = player.tile.add(Direction.WEST.delta)
        player.experience.player = player
        player.levels.link(player, PlayerLevels(player.experience))
        player.body.link(player.equipment, overrides)
        player.body.updateAll()
        player.appearance.displayName = player.name
        if (player.contains("new_player")) {
            accountDefinitions.add(player)
        }
        player.interfaces.displayMode = displayMode
        player.client = client
        (player.variables as PlayerVariables).client = client
        if (viewport) {
            player.viewport = Viewport()
        }
        player.collision = CollisionStrategyProvider.get(character = player)
        return true
    }

    /**
     * Send region load to a player
     */
    var loadCallback: (Player) -> Unit = {}

    fun spawn(player: Player, client: Client?) {
        client?.onDisconnecting {
            logout(player, false)
        }
        loadCallback.invoke(player)
        player.open(player.interfaces.gameFrame)
        Spawn.player(player)
        val offset = player.get<Long>("instance_offset")?.let { Delta(it) } ?: Delta.EMPTY
        val original = player.tile.minus(offset)
        for (def in Areas.get(original.zone)) {
            if (original in def.area) {
                Moved.enter(player, def.name, def)
            }
        }
    }

    suspend fun logout(player: Player, safely: Boolean) {
        if (player["logged_out", false]) {
            return
        }
        if (safely && player.contains("delay")) {
            player.message("You need to wait a few moments before you can log out.")
            return
        }
        if (!Despawn.logout(player)) {
            return
        }
        player["logged_out"] = true
        if (safely) {
            player.client?.logout()
            player.strongQueue("logout", onCancel = null) {
                // Make sure nothing else starts
            }
        }
        player.client?.disconnect()
        connectionQueue.disconnect {
            World.queue("logout_${player.accountName}", 1) {
                Players.remove(player)
            }
            val offset = player.get<Long>("instance_offset")?.let { Delta(it) } ?: Delta.EMPTY
            val original = player.tile.minus(offset)
            for (def in Areas.get(original.zone)) {
                if (original in def.area) {
                    Moved.exit(player, def.name, def)
                }
            }
            Despawn.player(player)
            player.queue.logout()
            player.softTimers.stopAll()
            player.timers.stopAll()
            saveQueue.save(player)
            AuditLog.event(player, "disconnected", player.tile)
        }
    }
}
