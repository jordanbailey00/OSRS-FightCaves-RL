package sim.engine.entity.character.player

import com.github.michaelbull.logging.InlineLogger
import kotlinx.coroutines.channels.Channel
import org.rsmod.game.pathfinder.collision.CollisionStrategy
import org.rsmod.game.pathfinder.flag.CollisionFlag
import sim.engine.client.instruction.InstructionTask
import sim.engine.client.ui.InterfaceOptions
import sim.engine.client.ui.Interfaces
import sim.engine.client.update.view.Viewport
import sim.engine.client.variable.PlayerVariables
import sim.engine.client.variable.Variables
import sim.engine.data.exchange.ExchangeHistory
import sim.engine.data.exchange.ExchangeOffer
import sim.engine.entity.character.Character
import sim.engine.entity.character.mode.EmptyMode
import sim.engine.entity.character.mode.Mode
import sim.engine.entity.character.mode.move.Steps
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.chat.clan.ClanRank
import sim.engine.entity.character.player.equip.BodyParts
import sim.engine.entity.character.player.skill.exp.Experience
import sim.engine.entity.character.player.skill.level.Levels
import sim.engine.get
import sim.engine.inv.Inventories
import sim.engine.queue.ActionQueue
import sim.engine.suspend.DialogueSuspension
import sim.engine.suspend.Suspension
import sim.engine.timer.TimerQueue
import sim.engine.timer.Timers
import sim.network.client.Client
import sim.network.client.Instruction
import sim.network.login.protocol.visual.PlayerVisuals
import sim.types.Tile
import kotlin.coroutines.Continuation

/**
 * A player controlled by client or bot
 */
class Player(
    override var index: Int = -1,
    override var tile: Tile = Tile.EMPTY,
    val inventories: Inventories = Inventories(),
    variables: MutableMap<String, Any> = mutableMapOf(),
    val experience: Experience = Experience(),
    override val levels: Levels = Levels(),
    val friends: MutableMap<String, ClanRank> = mutableMapOf(),
    val ignores: MutableList<String> = mutableListOf(),
    var client: Client? = null,
    var viewport: Viewport? = null,
    var accountName: String = "",
    var passwordHash: String = "",
    val body: BodyParts = BodyParts(),
    val offers: Array<ExchangeOffer> = Array(6) { ExchangeOffer() },
    val history: MutableList<ExchangeHistory> = mutableListOf(),
) : Character {

    override val visuals: PlayerVisuals = PlayerVisuals(body)
    override val blockMove = 0
    override val collisionFlag = CollisionFlag.BLOCK_PLAYERS

    init {
        if (index != -1) {
            visuals.hits.self = -index
        }
    }

    override val size: Int
        get() = appearance.size

    override var mode: Mode = EmptyMode
        set(value) {
            field.stop(value)
            if (value !is EmptyMode && get("debug", false)) {
                logger.debug { "$value" }
            }
            field = value
            value.start()
        }

    val instructions = Channel<Instruction>(capacity = InstructionTask.MAX_INSTRUCTIONS)
    val options = PlayerOptions(this)
    lateinit var interfaces: Interfaces
    lateinit var interfaceOptions: InterfaceOptions
    override lateinit var collision: CollisionStrategy
//    val area: AreaQueue = AreaQueue(this)

    val networked: Boolean
        get() = client != null && viewport != null

    override var suspension: Suspension? = null

    override var delay: Continuation<Unit>? = null

    var dialogueSuspension: DialogueSuspension<*>? = null

    override var queue = ActionQueue(this)

    /**
     * Always ticks
     */
    override var softTimers: Timers = TimerQueue(this)

    /**
     * Ticks while not delayed or has interface open
     */
    var timers = TimerQueue(this)

    override var variables: Variables = PlayerVariables(this, variables)

    override val steps = Steps(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Player
        return index == other.index
    }

    override fun hashCode(): Int = index

    override fun toString(): String = "Player($accountName, tile=$tile)"

    companion object {
        private val logger = InlineLogger("Player")
    }
}
